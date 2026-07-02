(ns translate
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [common :as c])
  (:import [java.time Instant]))

(defn paragraph-query [page-id]
  (format "SELECT ?p ?pos ?kind ?text WHERE { ?p a <https://schema.org/Paragraph> ; <https://schema.org/isPartOf> <%s> ; <https://schema.org/position> ?pos ; <https://schema.org/text> ?text . OPTIONAL { ?p <%sblockKind> ?kind . } } ORDER BY ?pos" page-id c/twp))

(defn fetch-paragraphs [ledger page-id]
  (let [result (c/fluree-query-json ledger (paragraph-query page-id))]
    (->> (get-in result [:results :bindings])
         (mapv (fn [b]
                 {:id (c/binding-value b :p)
                  :position (parse-long (c/binding-value b :pos))
                  :kind (or (c/binding-value b :kind) "p")
                  :text (c/binding-value b :text)})))))

(def prompt-path "prompts/translate-web-page-v1.md")

(defn prompt-template []
  (slurp prompt-path))

(defn rendered-prompt [paragraphs]
  (str/replace (prompt-template)
               "{{paragraphs_json}}"
               (json/generate-string
                (mapv #(select-keys % [:position :kind :text]) paragraphs)
                {:pretty true})))

(defn prompt-hash []
  (str "sha256:" (c/sha256 (prompt-template))))

(defn extract-json-array [s]
  (let [s (str/trim (str s))
        s (str/replace s #"(?s)^```(?:json)?\s*(.*?)\s*```$" "$1")
        start (str/index-of s "[")
        end (str/last-index-of s "]")]
    (when-not (and start end (< start end))
      (throw (ex-info "Pi output did not contain a JSON array" {:out s})))
    (subs s start (inc end))))

(defn validate-translations! [paragraphs rows]
  (let [rows (cond
               (vector? rows) rows
               ;; Cheshire may return a lazy seq/list depending on the input path.
               ;; Treat any sequential value as the JSON array we requested.
               (sequential? rows) (vec rows)
               :else (throw (ex-info "Translation result must be a JSON array" {:rows rows})))
        expected (set (map :position paragraphs))
        actual (set (map :position rows))]
    (when-not (= expected actual)
      (throw (ex-info "Translation positions do not match source paragraphs"
                      {:expected (sort expected) :actual (sort actual)})))
    (doseq [row rows]
      (when-not (and (integer? (:position row)) (string? (:ko row)) (not (str/blank? (:ko row))))
        (throw (ex-info "Invalid translation row" {:row row}))))
    rows))

(defn pi-options []
  {:provider "pi"
   :model (or (System/getenv "PI_TRANSLATION_MODEL") "pi-default")
   :thinking-effort (or (System/getenv "PI_TRANSLATION_THINKING") "pi-default")
   :prompt-name "translate-web-page/v1"
   :prompt-hash (prompt-hash)
   :translator-identity (or (System/getenv "TWP_TRANSLATOR_IDENTITY") "local-pi-user")
   :license (or (System/getenv "TWP_TRANSLATION_LICENSE") "unspecified")})

(defn pi-command [prompt {:keys [model thinking-effort]}]
  (cond-> ["pi" "-p" "--no-tools" "--no-context-files" "--no-skills"]
    (not= model "pi-default") (into ["--model" model])
    (not= thinking-effort "pi-default") (into ["--thinking" thinking-effort])
    true (conj prompt)))

(defn call-pi-translation [paragraphs]
  (let [opts (pi-options)]
    (println "[translate] calling Pi for" (count paragraphs) "paragraphs"
             "model=" (:model opts) "thinking=" (:thinking-effort opts))
    (let [prompt (rendered-prompt paragraphs)
          out (apply c/sh! (pi-command prompt opts))
          rows (json/parse-string (extract-json-array out) true)]
      {:rows (validate-translations! paragraphs rows)
       :run opts})))

(defn mock-translation [paragraph]
  ;; Explicit development-only mode. Real use calls Pi by default.
  (str "[MOCK KO] " (:text paragraph)))

(defn translate-in-one-go [paragraphs]
  (if (= "1" (System/getenv "MOCK_TRANSLATION"))
    (do
      (println "[translate] MOCK_TRANSLATION=1; not calling Pi")
      {:rows (mapv (fn [{:keys [position] :as paragraph}]
                     {:position position :ko (mock-translation paragraph)})
                   paragraphs)
       :run {:provider "mock" :model "mock-translation" :thinking-effort "none" :prompt-name "translate-web-page/v1"
             :prompt-hash (prompt-hash)
             :translator-identity (or (System/getenv "TWP_TRANSLATOR_IDENTITY") "local-pi-user")
             :license (or (System/getenv "TWP_TRANSLATION_LICENSE") "unspecified")}})
    (call-pi-translation paragraphs)))

(defn translations->jsonld [slug page-id paragraphs translations run]
  (let [by-pos (into {} (map (juxt :position identity) translations))
        run-id (c/translation-run-id slug)
        run-node {"@id" run-id
                  "@type" "TranslationRun"
                  "isPartOf" page-id
                  "llmProvider" (:provider run)
                  "llmModel" (:model run)
                  "thinkingEffort" (:thinking-effort run)
                  "promptName" (:prompt-name run)
                  "promptHash" (:prompt-hash run)
                  "translationStatus" "machine"
                  "translatorIdentity" (:translator-identity run)
                  "license" (:license run)
                  "generatedAt" (str (Instant/now))}]
    {"@context" c/jsonld-context
     "@graph" (into [run-node]
                    (mapv (fn [{:keys [id position]}]
                            (let [ko (:ko (get by-pos position))]
                              (when (str/blank? ko)
                                (throw (ex-info "Missing translation" {:position position})))
                              {"@id" (format "%s/paragraph/%04d" run-id (long position))
                               "@type" "TranslatedParagraph"
                               "position" position
                               "text" ko
                               "inLanguage" "ko"
                               "isPartOf" page-id
                               "translationOfWork" id
                               "translationRun" run-id
                               "translationStatus" "machine"}))
                          paragraphs))}))

(defn translate->fluree! [{:keys [ledger slug page-id]}]
  (let [page-id (or page-id (c/page-id slug))
        paragraphs (fetch-paragraphs ledger page-id)
        _ (when (empty? paragraphs)
            (throw (ex-info "No paragraphs found in Fluree" {:ledger ledger :page-id page-id})))
        {:keys [rows run]} (translate-in-one-go paragraphs)
        jsonld (translations->jsonld slug page-id paragraphs rows run)
        path (str "out/" slug ".translations.jsonld")]
    (c/write-json! path jsonld)
    (c/fluree-insert-file! ledger path)
    {:ledger ledger
     :slug slug
     :page-id page-id
     :translation-path path
     :translation-count (count rows)
     :llm-provider (:provider run)
     :llm-model (:model run)
     :thinking-effort (:thinking-effort run)}))

(defn -main [& args]
  (let [[ledger slug] (case (count args)
                        1 ["translate" (first args)]
                        2 [(first args) (second args)]
                        (throw (ex-info "Usage: bb translate <slug> OR bb translate <ledger> <slug>" {})))
        result (translate->fluree! {:ledger ledger :slug slug})]
    (println (json/generate-string result {:pretty true}))))
