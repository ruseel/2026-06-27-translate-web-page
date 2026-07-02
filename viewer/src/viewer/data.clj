(ns viewer.data
  (:require [clojure.string :as str]
            [common :as c]))

(defn pages-query []
  (format "SELECT ?page ?slug ?title ?url ?hash WHERE { ?page a <https://schema.org/WebPage> . OPTIONAL { ?page <%sslug> ?slug . } OPTIONAL { ?page <https://schema.org/name> ?title . } OPTIONAL { ?page <https://schema.org/url> ?url . } OPTIONAL { ?page <%ssourceContentHash> ?hash . } } ORDER BY ?title ?page"
          c/twp c/twp))

(defn page-query [page-id]
  (format "SELECT ?title ?url ?hash ?defuddle WHERE { <%s> a <https://schema.org/WebPage> . OPTIONAL { <%s> <https://schema.org/name> ?title . } OPTIONAL { <%s> <https://schema.org/url> ?url . } OPTIONAL { <%s> <%ssourceContentHash> ?hash . } OPTIONAL { <%s> <%sdefuddleVersion> ?defuddle . } } LIMIT 1"
          page-id page-id page-id page-id c/twp page-id c/twp))

(defn segment-query [page-id]
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

(defn fetch-pages [ledger]
  (let [rows (get-in (c/fluree-query-json ledger (pages-query)) [:results :bindings])]
    (->> rows
         (mapv (fn [row]
                 (let [page-id (bval row :page)
                       slug (or (bval row :slug) (fallback-slug page-id))]
                   {:id page-id
                    :slug slug
                    :title (or (bval row :title) slug "Untitled")
                    :url (bval row :url)
                    :sourceContentHash (bval row :hash)})))
         (sort-by (juxt :title :slug))
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

(defn row->segment-seed [row]
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

(defn all-segments [ledger page-id]
  (let [rows (get-in (c/fluree-query-json ledger (segment-query page-id)) [:results :bindings])]
    (->> rows
         (group-by #(bval % :pos))
         vals
         (mapv (fn [rows]
                 (let [seed (row->segment-seed (first rows))]
                   (assoc seed :candidates (distinct-candidates (keep candidate-from-row rows))))))
         (sort-by :position)
         vec)))

(defn matches-filter? [filters candidate]
  (and (or (str/blank? (:language filters)) (= (:language filters) (:language candidate)))
       (or (str/blank? (:status filters)) (= (:status filters) (:status candidate)))
       (or (str/blank? (:model filters)) (= (:model filters) (:model candidate)))))

(defn apply-filters [segments filters]
  (mapv (fn [segment]
          (update segment :candidates #(filterv (partial matches-filter? filters) %)))
        segments))

(defn sorted-values [segments k]
  (->> segments
       (mapcat :candidates)
       (keep k)
       (remove str/blank?)
       set
       sort
       vec))

(defn page-view [ledger slug filters]
  (let [page-id (c/page-id slug)
        segments (all-segments ledger page-id)
        filtered (apply-filters segments filters)
        candidates (mapcat :candidates segments)
        filtered-candidates (mapcat :candidates filtered)]
    {:ledger ledger
     :page (fetch-page-meta ledger page-id slug)
     :filters {:selected filters
               :models (sorted-values segments :model)
               :languages (sorted-values segments :language)
               :statuses (sorted-values segments :status)}
     :summary {:segmentCount (count segments)
               :candidateCount (count candidates)
               :filteredCandidateCount (count filtered-candidates)
               :modelCount (count (set (keep :model candidates)))}
     :segments filtered}))
