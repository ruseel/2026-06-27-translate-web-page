(ns viewer.client
  "CLJS source for the browser viewer. The current bb server serves viewer/src/viewer/client.js
  as the runnable browser artifact; this namespace mirrors that behavior so a shadow-cljs
  or cljs.main build can become the source of truth without changing the server/API."
  (:require [clojure.string :as str]))

(defonce state
  (atom {:ledger "translate"
         :pages []
         :slug ""
         :language ""
         :model ""
         :status ""
         :view nil}))

(defn by-id [id]
  (.getElementById js/document id))

(defn html [value]
  (-> (str (or value ""))
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&#39;")))

(defn option [value label selected]
  (str "<option value=\"" (html value) "\""
       (when (= value selected) " selected")
       ">" (html label) "</option>"))

(defn query-string [m]
  (let [params (js/URLSearchParams.)]
    (doseq [[k v] m]
      (when-not (str/blank? (str v))
        (.set params (name k) v)))
    (.toString params)))

(defn set-error! [message]
  (when-let [el (by-id "error")]
    (set! (.-innerHTML el)
          (if (str/blank? message)
            ""
            (str "<div class=\"error\">" (html message) "</div>")))))

(defn render-select-options! [el values selected all-label]
  (when el
    (set! (.-innerHTML el)
          (str (option "" all-label selected)
               (apply str (map #(option % % selected) values))))))

(defn render-pages! []
  (when-let [el (by-id "slug")]
    (let [{:keys [pages]} @state]
      (set! (.-innerHTML el)
            (apply str
                   (for [{:keys [slug title]} pages]
                     (option slug (str title " — " slug) (:slug @state))))))))

(defn render-summary! [{:keys [paragraphCount candidateCount filteredCandidateCount modelCount]}]
  (when-let [el (by-id "summary")]
    (set! (.-innerHTML el)
          (apply str
                 (map #(str "<span class=\"badge\">" (html %) "</span>")
                      [(str (or paragraphCount 0) " paragraphs")
                       (str (or filteredCandidateCount 0) "/" (or candidateCount 0) " candidates")
                       (str (or modelCount 0) " models")])))))

(defn short-id [s]
  (if (and s (> (count s) 18))
    (str "…" (subs s (- (count s) 18)))
    (or s "")))

(defn candidate-html [{:keys [id text language status provider model generatedAt translatorIdentity promptName run]}]
  (str "<article class=\"candidate\" data-candidate-id=\"" (html id) "\">"
       "<div class=\"candidate-head\"><strong>" (html model) "</strong>"
       "<span>" (html provider) "</span><span>" (html language) "</span><span>" (html status) "</span></div>"
       "<p lang=\"ko\">" (html text) "</p>"
       "<details><summary>provenance</summary><dl>"
       "<dt>generated</dt><dd>" (html generatedAt) "</dd>"
       "<dt>translator</dt><dd>" (html translatorIdentity) "</dd>"
       "<dt>prompt</dt><dd>" (html promptName) "</dd>"
       "<dt>run</dt><dd title=\"" (html run) "\">" (html (short-id run)) "</dd>"
       "</dl></details></article>"))

(defn paragraph-html [{:keys [position kind source candidates]}]
  (str "<section class=\"paragraph\" id=\"paragraph-" position "\" data-paragraph=\"" position "\">"
       "<div class=\"source\"><div class=\"eyebrow\">#" position " · " (html kind) "</div>"
       "<p lang=\"en\">" (html source) "</p></div>"
       "<div class=\"translations\">"
       (if (seq candidates)
         (apply str (map candidate-html candidates))
         "<div class=\"empty\">No translations match these filters.</div>")
       "</div></section>"))

(defn render-view! []
  (let [{:keys [view language model status]} @state
        {:keys [page filters summary paragraphs]} view]
    (when view
      (set! (.-title js/document) (str (or (:title page) "Fluree Translation Reader") " · Translation Reader"))
      (when-let [el (by-id "page-title")] (set! (.-textContent el) (or (:title page) "Fluree Translation Reader")))
      (when-let [el (by-id "page-url")] (set! (.-textContent el) (or (:url page) "")))
      (render-summary! summary)
      (render-select-options! (by-id "language") (:languages filters) language "All languages")
      (render-select-options! (by-id "model") (:models filters) model "All models")
      (render-select-options! (by-id "status") (:statuses filters) status "All statuses")
      (when-let [el (by-id "paragraphs")]
        (set! (.-innerHTML el) (apply str (map paragraph-html paragraphs)))))))

(defn fetch-json [url]
  (-> (js/fetch url #js {:headers #js {:Accept "application/json"}})
      (.then (fn [res]
               (-> (.json res)
                   (.then (fn [data]
                            (if (or (not (.-ok res)) (.-error data))
                              (throw (js/Error. (or (.-error data) (str "HTTP " (.-status res)))))
                              (js->clj data :keywordize-keys true)))))))))

(defn load-pages! []
  (set-error! "")
  (-> (fetch-json (str "/api/pages?" (query-string {:ledger (:ledger @state)})))
      (.then (fn [{:keys [pages]}]
               (swap! state assoc :pages pages)
               (when-not (some #(= (:slug %) (:slug @state)) pages)
                 (swap! state assoc :slug (:slug (first pages))))
               (render-pages!)))))

(defn load-view! []
  (when-not (str/blank? (:slug @state))
    (set-error! "")
    (-> (fetch-json (str "/api/page?" (query-string (select-keys @state [:ledger :slug :language :model :status]))))
        (.then (fn [view]
                 (swap! state assoc :view view)
                 (.replaceState js/history nil "" (str "/?" (query-string (select-keys @state [:ledger :slug :language :model :status]))))
                 (render-view!))))))

(defn sync-from-controls! []
  (swap! state assoc
         :ledger (or (some-> (by-id "ledger") .-value) (:ledger @state))
         :slug (or (some-> (by-id "slug") .-value) (:slug @state))
         :language (or (some-> (by-id "language") .-value) "")
         :model (or (some-> (by-id "model") .-value) "")
         :status (or (some-> (by-id "status") .-value) "")))

(defn catch-error [promise]
  (.catch promise #(set-error! (.-message %))))

(defn wire! []
  (when-let [boot (by-id "boot-data")]
    (let [data (js->clj (js/JSON.parse (.-textContent boot)) :keywordize-keys true)]
      (reset! state {:ledger (:ledger data)
                     :pages (:pages data)
                     :slug (:selectedSlug data)
                     :language (get-in data [:view :filters :selected :language] "")
                     :model (get-in data [:view :filters :selected :model] "")
                     :status (get-in data [:view :filters :selected :status] "")
                     :view (:view data)})))
  (render-pages!)
  (render-view!)
  (when-let [el (by-id "ledger")]
    (.addEventListener el "change" #(do (sync-from-controls!) (catch-error (-> (load-pages!) (.then load-view!))))))
  (when-let [el (by-id "slug")]
    (.addEventListener el "change" #(do (sync-from-controls!) (swap! state assoc :language "" :model "" :status "") (catch-error (load-view!)))))
  (doseq [id ["language" "model" "status"]]
    (when-let [el (by-id id)]
      (.addEventListener el "change" #(do (sync-from-controls!) (catch-error (load-view!))))))
  (when-let [el (by-id "refresh")]
    (.addEventListener el "click" #(do (sync-from-controls!) (catch-error (load-view!))))))

(.addEventListener js/document "DOMContentLoaded" wire! #js {:once true})
