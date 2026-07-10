(ns viewer.data
  (:require [clojure.string :as str]
            [common :as c]))

(defn pages-query []
  (format (str "SELECT ?page ?slug ?title ?url ?hash ?creationDate ?publishedDate ?articleDateText WHERE { "
               "?page a <https://schema.org/WebPage> . "
               "OPTIONAL { ?page <%sslug> ?slug . } "
               "OPTIONAL { ?page <https://schema.org/name> ?title . } "
               "OPTIONAL { ?page <https://schema.org/url> ?url . } "
               "OPTIONAL { ?page <%ssourceContentHash> ?hash . } "
               "OPTIONAL { ?page <https://schema.org/dateCreated> ?creationDate . } "
               "OPTIONAL { ?page <https://schema.org/datePublished> ?publishedDate . } "
               "OPTIONAL { ?dateParagraph a <https://schema.org/Paragraph> ; "
               "<https://schema.org/isPartOf> ?page ; "
               "<https://schema.org/position> 1 ; "
               "<https://schema.org/text> ?articleDateText ; "
               "<%sblockKind> \"date\" . } "
               "} ORDER BY ?title ?page")
          c/twp c/twp c/twp))

(defn page-query [page-id]
  (format "SELECT ?title ?url ?hash ?defuddle WHERE { <%s> a <https://schema.org/WebPage> . OPTIONAL { <%s> <https://schema.org/name> ?title . } OPTIONAL { <%s> <https://schema.org/url> ?url . } OPTIONAL { <%s> <%ssourceContentHash> ?hash . } OPTIONAL { <%s> <%sdefuddleVersion> ?defuddle . } } LIMIT 1"
          page-id page-id page-id page-id c/twp page-id c/twp))

(defn paragraph-query [page-id]
  (format (str "SELECT ?p ?pos ?kind ?source ?t ?translation ?lang ?status ?run "
               "?provider ?model ?thinking ?prompt ?promptHash ?translator ?license ?at WHERE { "
               "?p a <https://schema.org/Paragraph> ; "
               "<https://schema.org/isPartOf> <%s> ; "
               "<https://schema.org/position> ?pos ; "
               "<https://schema.org/text> ?source . "
               "OPTIONAL { ?p <%sblockKind> ?kind . } "
               "OPTIONAL { ?t a <%sTranslatedParagraph> ; "
               "<https://schema.org/translationOfWork> ?p ; "
               "<https://schema.org/text> ?translation . "
               "OPTIONAL { ?t <https://schema.org/inLanguage> ?lang . } "
               "OPTIONAL { ?t <%stranslationStatus> ?status . } "
               "OPTIONAL { ?t <%stranslationRun> ?run . "
               "OPTIONAL { ?run <%sllmProvider> ?provider . } "
               "OPTIONAL { ?run <%sllmModel> ?model . } "
               "OPTIONAL { ?run <%sthinkingEffort> ?thinking . } "
               "OPTIONAL { ?run <%spromptName> ?prompt . } "
               "OPTIONAL { ?run <%spromptHash> ?promptHash . } "
               "OPTIONAL { ?run <%stranslatorIdentity> ?translator . } "
               "OPTIONAL { ?run <https://schema.org/license> ?license . } "
               "OPTIONAL { ?run <https://schema.org/dateCreated> ?at . } "
               "} } } ORDER BY ?pos ?at ?model")
          page-id c/twp c/twp c/twp c/twp c/twp c/twp c/twp c/twp c/twp c/twp c/twp))

(defn fallback-slug [page-id]
  (some-> page-id (str/split #"/") last))

(defn bval [row k]
  (c/binding-value row k))

(def month-number
  {"jan" 1 "january" 1
   "feb" 2 "february" 2
   "mar" 3 "march" 3
   "apr" 4 "april" 4
   "may" 5
   "jun" 6 "june" 6
   "jul" 7 "july" 7
   "aug" 8 "august" 8
   "sep" 9 "sept" 9 "september" 9
   "oct" 10 "october" 10
   "nov" 11 "november" 11
   "dec" 12 "december" 12})

(defn creation-date-sort-value [s]
  (let [t (some-> s str/trim)]
    (when-not (str/blank? t)
      (or (when-let [[_ year month day] (re-find #"^(\d{4})-(\d{1,2})(?:-(\d{1,2}))?" t)]
            (+ (* (parse-long year) 10000)
               (* (parse-long month) 100)
               (parse-long (or day "1"))))
          (when-let [[_ month year] (re-find #"(?i)^([a-z]{3,9})\.?\s+(\d{4})" t)]
            (when-let [m (month-number (str/lower-case month))]
              (+ (* (parse-long year) 10000) (* m 100) 1)))
          (when-let [[_ year month] (re-find #"^(\d{4})년\s*(\d{1,2})월" t)]
            (+ (* (parse-long year) 10000) (* (parse-long month) 100) 1))
          (when-let [[_ year] (re-find #"^(\d{4})$" t)]
            (* (parse-long year) 10000))))))

(defn page-from-rows [rows]
  (let [row (first rows)
        page-id (bval row :page)
        slug (or (bval row :slug) (fallback-slug page-id))
        creation-date (or (some #(bval % :creationDate) rows)
                          (some #(bval % :publishedDate) rows)
                          (some #(bval % :articleDateText) rows))]
    {:id page-id
     :slug slug
     :title (or (bval row :title) slug "Untitled")
     :url (bval row :url)
     :sourceContentHash (bval row :hash)
     :creationDate creation-date
     :creationDateSort (creation-date-sort-value creation-date)}))

(defn page-sort-key [{:keys [creationDateSort title slug]}]
  [(if creationDateSort (- creationDateSort) Long/MAX_VALUE)
   (or title "")
   (or slug "")])

(defn fetch-pages [ledger]
  (let [rows (get-in (c/fluree-query-json ledger (pages-query)) [:results :bindings])]
    (->> rows
         (group-by #(bval % :page))
         vals
         (mapv page-from-rows)
         (sort-by page-sort-key)
         vec)))

(defn fetch-page-meta [ledger page-id slug]
  (let [row (first (get-in (c/fluree-query-json ledger (page-query page-id)) [:results :bindings]))]
    {:id page-id
     :slug slug
     :title (or (bval row :title) slug "Translated Web Page")
     :url (bval row :url)
     :sourceContentHash (bval row :hash)
     :defuddleVersion (bval row :defuddle)}))

(defn candidate-from-row [row]
  (when-let [id (bval row :t)]
    {:id id
     :text (or (bval row :translation) "")
     :language (or (bval row :lang) "unknown")
     :status (or (bval row :status) "unknown")
     :run (bval row :run)
     :provider (or (bval row :provider) "unknown")
     :model (or (bval row :model) "unknown")
     :thinkingEffort (bval row :thinking)
     :promptName (bval row :prompt)
     :promptHash (bval row :promptHash)
     :translatorIdentity (bval row :translator)
     :license (bval row :license)
     :generatedAt (or (bval row :at) "")}))

(defn row->paragraph-seed [row]
  {:id (bval row :p)
   :position (parse-long (bval row :pos))
   :kind (or (bval row :kind) "p")
   :source (bval row :source)
   :candidates []})

(defn distinct-candidates [candidates]
  (->> candidates
       (remove #(str/blank? (:text %)))
       (reduce (fn [acc c]
                 (assoc acc (:id c) c))
               {})
       vals
       (sort-by (juxt :generatedAt :model :id))
       vec))

(defn all-paragraphs [ledger page-id]
  (let [rows (get-in (c/fluree-query-json ledger (paragraph-query page-id)) [:results :bindings])]
    (->> rows
         (group-by #(bval % :pos))
         vals
         (mapv (fn [rows]
                 (let [seed (row->paragraph-seed (first rows))]
                   (assoc seed :candidates (distinct-candidates (keep candidate-from-row rows))))))
         (sort-by :position)
         vec)))

(defn matches-filter? [filters candidate]
  (and (or (str/blank? (:language filters)) (= (:language filters) (:language candidate)))
       (or (str/blank? (:status filters)) (= (:status filters) (:status candidate)))
       (or (str/blank? (:model filters)) (= (:model filters) (:model candidate)))))

(defn apply-filters [paragraphs filters]
  (mapv (fn [paragraph]
          (update paragraph :candidates #(filterv (partial matches-filter? filters) %)))
        paragraphs))

(defn sorted-values [paragraphs k]
  (->> paragraphs
       (mapcat :candidates)
       (keep k)
       (remove str/blank?)
       set
       sort
       vec))

(defn page-view [ledger slug filters]
  (let [page-id (c/page-id slug)
        paragraphs (all-paragraphs ledger page-id)
        filtered (apply-filters paragraphs filters)
        candidates (mapcat :candidates paragraphs)
        filtered-candidates (mapcat :candidates filtered)]
    {:ledger ledger
     :page (fetch-page-meta ledger page-id slug)
     :filters {:selected filters
               :models (sorted-values paragraphs :model)
               :languages (sorted-values paragraphs :language)
               :statuses (sorted-values paragraphs :status)}
     :summary {:paragraphCount (count paragraphs)
               :candidateCount (count candidates)
               :filteredCandidateCount (count filtered-candidates)
               :modelCount (count (set (keep :model candidates)))}
     :paragraphs filtered}))
