(ns status.core
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [common.core :as c]))

(defn status-query [page-id]
  (format "SELECT ?title ?url ?hash ?defuddle ?p ?t ?tp ?run ?model ?provider ?status ?at WHERE { <%s> a <https://schema.org/WebPage> . OPTIONAL { <%s> <https://schema.org/name> ?title . } OPTIONAL { <%s> <https://schema.org/url> ?url . } OPTIONAL { <%s> <%ssourceContentHash> ?hash . } OPTIONAL { <%s> <%sdefuddleVersion> ?defuddle . } OPTIONAL { ?p a <https://schema.org/Paragraph> ; <https://schema.org/isPartOf> <%s> . } OPTIONAL { ?t a <%sTranslatedParagraph> ; <https://schema.org/isPartOf> <%s> ; <https://schema.org/translationOfWork> ?tp ; <%stranslationRun> ?run . OPTIONAL { ?t <%stranslationStatus> ?status . } OPTIONAL { ?run <%sllmModel> ?model ; <%sllmProvider> ?provider . OPTIONAL { ?run <https://schema.org/dateCreated> ?at . } } } }"
          page-id page-id page-id page-id c/twp page-id c/twp page-id c/twp page-id c/twp c/twp c/twp c/twp))

(defn summarize [bindings]
  (let [v #(c/binding-value %1 %2)
        first-row (first bindings)
        paragraphs (->> bindings (keep #(v % :p)) set count)
        translation-versions (->> bindings (keep #(v % :t)) set count)
        translated-paragraphs (->> bindings (keep #(v % :tp)) set count)
        runs (->> bindings
                  (keep (fn [b]
                          (when-let [run (v b :run)]
                            {:run run
                             :model (v b :model)
                             :provider (v b :provider)
                             :status (v b :status)
                             :at (or (v b :at) "")})))
                  (group-by :run)
                  vals
                  (map first)
                  (sort-by :at)
                  vec)
        latest (last runs)]
    {:title (or (v first-row :title) "Translated Web Page")
     :url (v first-row :url)
     :source-content-hash (v first-row :hash)
     :defuddle-version (v first-row :defuddle)
     :paragraphs paragraphs
     :translated-paragraphs translated-paragraphs
     :translation-versions translation-versions
     :translation-runs (count runs)
     :latest-run latest
     :missing-translations (max 0 (- paragraphs translated-paragraphs))}))

(defn inspect! [{:keys [ledger slug page-id]}]
  (let [page-id (or page-id (c/page-id slug))
        bindings (get-in (c/fluree-query-json ledger (status-query page-id)) [:results :bindings])
        summary (summarize bindings)]
    (println "Article:" (:title summary))
    (when-not (str/blank? (:url summary)) (println "URL:" (:url summary)))
    (when-not (str/blank? (:source-content-hash summary)) (println "Source hash:" (:source-content-hash summary)))
    (when-not (str/blank? (:defuddle-version summary)) (println "Defuddle:" (:defuddle-version summary)))
    (println "Paragraphs:" (:paragraphs summary))
    (println "Translated paragraphs:" (:translated-paragraphs summary))
    (println "Translation versions:" (:translation-versions summary))
    (println "Missing translations:" (:missing-translations summary))
    (println "Translation runs:" (:translation-runs summary))
    (when-let [latest (:latest-run summary)]
      (println "Latest run:" (:provider latest) (:model latest) (:status latest) (:at latest)))
    summary))

(defn -main [& args]
  (let [[ledger slug] (case (count args)
                        1 ["translate" (first args)]
                        2 [(first args) (second args)]
                        (throw (ex-info "Usage: bb status <slug> OR bb status <ledger> <slug>" {})))
        result (inspect! {:ledger ledger :slug slug})]
    (println (json/generate-string result {:pretty true}))))
