(ns fetch
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str]
            [common :as c]))

(def defuddle-version "0.19.1")

(defn local-json-path? [s]
  (and s (fs/exists? s) (str/ends-with? (str s) ".json")))

(defn fetch-defuddle!
  "Return {:path ... :data ...}. If input is a local JSON file, use it as a Defuddle snapshot."
  [input slug]
  (c/ensure-out!)
  (let [raw-path (str "out/" slug ".defuddle.json")]
    (if (local-json-path? input)
      (do
        (println "[fetch] using local Defuddle JSON" input)
        (fs/copy input raw-path {:replace-existing true})
        {:path raw-path :data (c/read-json raw-path)})
      (do
        (println "[fetch] running Defuddle for" input)
        (let [tmp (str raw-path ".tmp")]
          (let [r (p/shell {:out tmp :err :string :continue true}
                           "npx" "--yes" (str "defuddle@" defuddle-version) "parse" input "--json")]
            (when-not (zero? (:exit r))
              (throw (ex-info (str "Defuddle failed\n" (:err r)) {:result r}))))
          ;; Re-read and pretty-print so downstream diffs are stable.
          (let [data (c/read-json tmp)]
            (c/write-json! raw-path data)
            (fs/delete-if-exists tmp)
            {:path raw-path :data data}))))))

(defn paragraph-kind [text]
  (let [t (str/trim text)]
    (cond
      (re-find #"^>" t) "quote"
      (re-find #"^#{1}\s+" t) "h1"
      (re-find #"^#{2}\s+" t) "h2"
      (re-find #"^#{3}\s+" t) "h3"
      (re-find #"^[-*+]\s+" t) "li"
      (re-find #"(?i)^\**thanks\**\b" t) "thanks"
      (and (< (count t) 40) (re-find #"\b(\d{4}|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)" (str/lower-case t))) "date"
      :else "p")))

(defn clean-inline-markdown [text]
  (-> text
      ;; PG footnotes often arrive from Defuddle as \[[2](#f2n)\]. Store a
      ;; readable marker so translation prompts do not encourage literal
      ;; Markdown artifacts in the Korean text.
      (str/replace #"\\?\[\[(\d+)\]\(#[^)\s]+\)\\?\]" "[$1]")
      (str/replace #"\[([^\]\n]{1,120})\]\((https?://[^)\s]+|mailto:[^)\s]+|#[^)\s]+)\)" "$1")
      (str/replace #"\\([\[\]])" "$1")
      (str/replace #"\*\*([^*]+)\*\*" "$1")
      (str/replace #"__([^_]+)__" "$1")))

(defn clean-markdown [kind text]
  (let [t (-> text
              (str/replace #"\s+" " ")
              str/trim)
        t (case kind
            "quote" (-> t (str/replace #"^>\s*" "") str/trim)
            "h1" (-> t (str/replace #"^#\s*" "") str/trim)
            "h2" (-> t (str/replace #"^##\s*" "") str/trim)
            "h3" (-> t (str/replace #"^###\s*" "") str/trim)
            "li" (-> t (str/replace #"^[-*+]\s*" "") str/trim)
            "thanks" (-> t (str/replace #"\*\*" "") str/trim)
            t)]
    (clean-inline-markdown t)))

(defn defuddle->paragraphs [data]
  (let [body (or (:contentMarkdown data) (:content data))]
    (when (str/blank? body)
      (throw (ex-info "Defuddle JSON has no contentMarkdown/content" {:keys (keys data)})))
    (->> (str/split body #"\n\s*\n+")
         (map str/trim)
         (remove str/blank?)
         (map-indexed (fn [i raw]
                        (let [kind (paragraph-kind raw)]
                          {:position (inc i)
                           :kind kind
                           :text (clean-markdown kind raw)})))
         vec)))

(defn article-creation-date [data paragraphs]
  (or (not-empty (str/trim (str (or (:published data) ""))))
      (some (fn [{:keys [kind text]}]
              (when (= kind "date") text))
            paragraphs)))

(defn defuddle->jsonld [source-url slug data]
  (let [title (or (:title data) source-url "Untitled")
        page-id (c/page-id slug)
        paragraphs (defuddle->paragraphs data)
        creation-date (article-creation-date data paragraphs)
        source-content (str/join "\n\n" (map :text paragraphs))
        paragraph-nodes (mapv (fn [{:keys [position kind text]}]
                                {"@id" (c/paragraph-id slug position)
                                 "@type" "Paragraph"
                                 "position" position
                                 "blockKind" kind
                                 "text" text
                                 "inLanguage" "en"
                                 "isPartOf" page-id})
                              paragraphs)
        page-node (cond-> {"@id" page-id
                           "@type" "WebPage"
                           "url" source-url
                           "sourceUrl" source-url
                           "slug" slug
                           "sourceContentHash" (str "sha256:" (c/sha256 source-content))
                           "defuddleVersion" defuddle-version
                           "name" title
                           "headline" title
                           "hasPart" (mapv #(select-keys % ["@id"]) paragraph-nodes)}
                    (not (str/blank? creation-date))
                    (assoc "creationDate" creation-date))]
    {"@context" c/jsonld-context
     "@graph" (into [page-node] paragraph-nodes)}))

(defn fetch->fluree! [{:keys [input ledger]}]
  (let [source (or input "article.json")
        local-data (when (local-json-path? source) (c/read-json source))
        slug (c/slugify (if local-data
                          (or (:url local-data) (:title local-data) source)
                          source))
        {:keys [data path]} (fetch-defuddle! source slug)
        jsonld (defuddle->jsonld (if local-data (or (:url data) "file:article.json") source) slug data)
        jsonld-path (str "out/" slug ".jsonld")]
    (c/write-json! jsonld-path jsonld)
    (c/fluree-insert-file! ledger jsonld-path)
    {:ledger ledger
     :slug slug
     :page-id (c/page-id slug)
     :defuddle-path path
     :jsonld-path jsonld-path
     :paragraph-count (dec (count (get jsonld "@graph"))) }))

(defn -main [& args]
  (let [input (first args)
        ledger (or (second args) "translate")
        result (fetch->fluree! {:input input :ledger ledger})]
    (println (json/generate-string result {:pretty true}))))
