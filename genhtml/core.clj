(ns genhtml.core
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [common.core :as c]))

(defn pair-query [page-id]
  (format "SELECT ?pos ?kind ?en ?ko ?run ?at WHERE { ?p a <https://schema.org/Paragraph> ; <https://schema.org/isPartOf> <%s> ; <https://schema.org/position> ?pos ; <https://schema.org/text> ?en . OPTIONAL { ?p <https://example.org/translate-web-page#blockKind> ?kind . } OPTIONAL { ?t a <https://example.org/translate-web-page#TranslatedParagraph> ; <https://schema.org/translationOfWork> ?p ; <https://schema.org/text> ?ko . OPTIONAL { ?t <https://example.org/translate-web-page#translationRun> ?run . ?run <https://schema.org/dateCreated> ?at . } } } ORDER BY ?pos ?at" page-id))

(defn page-title-query [page-id]
  (format "SELECT ?title ?url WHERE { <%s> a <https://schema.org/WebPage> . OPTIONAL { <%s> <https://schema.org/name> ?title . } OPTIONAL { <%s> <https://schema.org/url> ?url . } } LIMIT 1" page-id page-id page-id))

(defn fetch-title [ledger page-id]
  (let [rows (get-in (c/fluree-query-json ledger (page-title-query page-id)) [:results :bindings])
        row (first rows)]
    {:title (or (c/binding-value row :title) "Translated Web Page")
     :url (c/binding-value row :url)}))

(defn fetch-pairs [ledger page-id]
  (let [result (c/fluree-query-json ledger (pair-query page-id))]
    (->> (get-in result [:results :bindings])
         (map (fn [b]
                {:position (parse-long (c/binding-value b :pos))
                 :kind (or (c/binding-value b :kind) "p")
                 :en (c/binding-value b :en)
                 :ko (or (c/binding-value b :ko) "번역 없음")
                 :run (c/binding-value b :run)
                 :at (or (c/binding-value b :at) "")}))
         ;; A page may be translated multiple times. Render one row per source
         ;; paragraph, preferring the newest TranslationRun metadata.
         (group-by :position)
         vals
         (mapv (fn [rows]
                 (select-keys (last (sort-by :at rows)) [:position :kind :en :ko])))
         (sort-by :position)
         vec)))

(defn tag-for [kind]
  (case kind
    "h1" "h1"
    "h2" "h2"
    "h3" "h3"
    "quote" "blockquote"
    "date" "p"
    "thanks" "p"
    "li" "p"
    "p"))

(defn text-node [lang kind text]
  (let [tag (tag-for kind)
        prefix (if (= kind "li") "• " "")]
    (format "<%s lang=\"%s\">%s%s</%s>" tag lang prefix (c/html-escape text) tag)))

(defn render-html [{:keys [title url rows]}]
  (let [safe-title (c/html-escape title)
        source (when-not (str/blank? url)
                 (format "<p class=\"source\"><a href=\"%s\">%s</a></p>" (c/html-escape url) (c/html-escape url)))]
    (str "<!doctype html>\n"
         "<html lang=\"ko\">\n<head>\n"
         "  <meta charset=\"utf-8\">\n"
         "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
         "  <title>" safe-title "</title>\n"
         "  <style>\n"
         "    :root { color-scheme: light; font-family: ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, sans-serif; }\n"
         "    body { margin: 0; background: #f6f3ed; color: #1f2933; }\n"
         "    main { max-width: 1180px; margin: 0 auto; padding: 40px 24px 80px; }\n"
         "    header { margin-bottom: 28px; }\n"
         "    .source a { color: #667085; }\n"
         "    .row { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 28px; padding: 18px 0; border-top: 1px solid rgba(31,41,51,.14); }\n"
         "    .cell { background: rgba(255,255,255,.54); border-radius: 14px; padding: 18px 20px; line-height: 1.72; }\n"
         "    .original { color: #344054; }\n"
         "    .translation { color: #111827; }\n"
         "    blockquote { margin: 0; padding-left: 1rem; border-left: 4px solid #d0a85c; }\n"
         "    @media (max-width: 760px) { .row { grid-template-columns: 1fr; gap: 10px; } main { padding: 24px 14px 60px; } }\n"
         "  </style>\n"
         "</head>\n<body>\n<main>\n"
         "  <header>\n"
         "    <h1>" safe-title "</h1>\n"
         (or source "") "\n"
         "  </header>\n"
         (apply str
                (for [{:keys [position kind en ko]} rows]
                  (str "  <section class=\"row kind-" (c/html-escape kind) "\" id=\"row-" position "\">\n"
                       "    <div class=\"cell original\">" (text-node "en" kind en) "</div>\n"
                       "    <div class=\"cell translation\">" (text-node "ko" kind ko) "</div>\n"
                       "  </section>\n")))
         "</main>\n</body>\n</html>\n")))

(defn genhtml! [{:keys [ledger slug page-id]}]
  (let [page-id (or page-id (c/page-id slug))
        meta (fetch-title ledger page-id)
        rows (fetch-pairs ledger page-id)
        _ (when (empty? rows)
            (throw (ex-info "No paragraph pairs found" {:ledger ledger :page-id page-id})))
        html (render-html (assoc meta :rows rows))
        path (str "out/" slug ".translation-pairs.html")]
    (spit path html)
    {:ledger ledger
     :slug slug
     :page-id page-id
     :html-path path
     :row-count (count rows)}))

(defn -main [& args]
  (let [[ledger slug] (case (count args)
                        1 ["translate" (first args)]
                        2 [(first args) (second args)]
                        (throw (ex-info "Usage: bb genhtml <slug> OR bb genhtml <ledger> <slug>" {})))
        result (genhtml! {:ledger ledger :slug slug})]
    (println (json/generate-string result {:pretty true}))))
