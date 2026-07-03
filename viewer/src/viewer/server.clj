(ns viewer.server
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [org.httpkit.server :as http]
            [viewer.data :as data]
            [viewer.v2 :as v2]
            [common :as c])
  (:import [java.net URLDecoder]
           [java.nio.charset StandardCharsets]))

(def default-ledger "translate")
(def default-port 8123)

(defn decode-url [s]
  (URLDecoder/decode (str (or s "")) (.name StandardCharsets/UTF_8)))

(defn parse-query [raw]
  (if (str/blank? raw)
    {}
    (->> (str/split raw #"&")
         (remove str/blank?)
         (map (fn [part]
                (let [[k v] (str/split part #"=" 2)]
                  [(keyword (decode-url k)) (decode-url v)])))
         (into {}))))

(defn request-query [request]
  (parse-query (:query-string request)))

(defn response [status content-type body]
  {:status status
   :headers {"Content-Type" (str content-type "; charset=utf-8")
             "Cache-Control" "no-store"}
   :body body})

(defn json-response [value]
  (response 200 "application/json" (json/generate-string value {:pretty true})))

(defn json-safe [x]
  (cond
    (nil? x) nil
    (string? x) x
    (number? x) x
    (boolean? x) x
    (keyword? x) (name x)
    (symbol? x) (str x)
    (map? x) (into {} (map (fn [[k v]] [(json-safe k) (json-safe v)])) x)
    (coll? x) (mapv json-safe x)
    :else (str x)))

(defn error-response [status message data]
  (response status "application/json"
            (json/generate-string {:error message :data (json-safe data)} {:pretty true})))

(defn not-found []
  (error-response 404 "Not found" {}))

(defn param [query k default]
  (let [v (get query k)]
    (if (str/blank? v) default v)))

(defn filters-from-query [query]
  {:language (param query :language "")
   :model (param query :model "")
   :status (param query :status "")})

(defn page-list-options [pages selected-slug]
  (apply str
         (for [{:keys [slug title]} pages]
           (format "<option value=\"%s\"%s>%s</option>"
                   (c/html-escape slug)
                   (if (= slug selected-slug) " selected" "")
                   (c/html-escape (str title " — " slug))))))

(defn option-list [values selected all-label]
  (str (format "<option value=\"\">%s</option>" (c/html-escape all-label))
       (apply str
              (for [v values]
                (format "<option value=\"%s\"%s>%s</option>"
                        (c/html-escape v)
                        (if (= v selected) " selected" "")
                        (c/html-escape v))))))

(defn short-id [s]
  (if (and s (> (count s) 18))
    (str "…" (subs s (- (count s) 18)))
    (or s "")))

(defn render-candidate [{:keys [id text language status provider model generatedAt translatorIdentity promptName run]}]
  (str "<article class=\"candidate\" data-candidate-id=\"" (c/html-escape id) "\">"
       "<div class=\"candidate-head\">"
       "<strong>" (c/html-escape model) "</strong>"
       "<span>" (c/html-escape provider) "</span>"
       "<span>" (c/html-escape language) "</span>"
       "<span>" (c/html-escape status) "</span>"
       "</div>"
       "<p lang=\"ko\">" (c/html-escape text) "</p>"
       "<details><summary>provenance</summary>"
       "<dl>"
       "<dt>generated</dt><dd>" (c/html-escape generatedAt) "</dd>"
       "<dt>translator</dt><dd>" (c/html-escape translatorIdentity) "</dd>"
       "<dt>prompt</dt><dd>" (c/html-escape promptName) "</dd>"
       "<dt>run</dt><dd title=\"" (c/html-escape run) "\">" (c/html-escape (short-id run)) "</dd>"
       "</dl></details>"
       "</article>"))

(defn render-segment [{:keys [position kind source candidates]}]
  (str "<section class=\"segment\" id=\"segment-" position "\" data-segment=\"" position "\">"
       "<div class=\"source\">"
       "<div class=\"eyebrow\">#" position " · " (c/html-escape kind) "</div>"
       "<p lang=\"en\">" (c/html-escape source) "</p>"
       "</div>"
       "<div class=\"translations\">"
       (if (seq candidates)
         (apply str (map render-candidate candidates))
         "<div class=\"empty\">No translations match these filters.</div>")
       "</div>"
       "</section>"))

(defn render-segments [segments]
  (apply str (map render-segment segments)))

(defn render-summary [{:keys [segmentCount candidateCount filteredCandidateCount modelCount]}]
  (str "<span class=\"badge\">" segmentCount " segments</span>"
       "<span class=\"badge\">" filteredCandidateCount "/" candidateCount " candidates</span>"
       "<span class=\"badge\">" modelCount " models</span>"))

(defn app-css []
  (str ""
       ":root{color-scheme:light;font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,sans-serif;background:#f6f3ed;color:#172033}"
       "body{margin:0}.app{max-width:1440px;margin:0 auto;padding:28px 24px 72px}.hero{display:flex;gap:20px;align-items:flex-start;justify-content:space-between;margin-bottom:22px}"
       "h1{font-size:clamp(28px,4vw,54px);line-height:1;margin:0 0 10px}.subtle{color:#667085}.toolbar{position:sticky;top:0;z-index:2;display:grid;grid-template-columns:1fr 2fr repeat(3,1fr) auto;gap:10px;padding:12px;margin:0 -12px 24px;background:rgba(246,243,237,.92);backdrop-filter:blur(14px);border-bottom:1px solid rgba(23,32,51,.1)}"
       "label{display:grid;gap:4px;font-size:12px;text-transform:uppercase;letter-spacing:.06em;color:#667085}select,input,button{font:inherit;border:1px solid rgba(23,32,51,.18);border-radius:12px;background:#fff;padding:10px 12px}button{cursor:pointer;background:#172033;color:white;border-color:#172033}.summary{display:flex;flex-wrap:wrap;gap:8px;margin-top:12px}.badge{border:1px solid rgba(23,32,51,.14);background:rgba(255,255,255,.65);border-radius:999px;padding:5px 10px;font-size:13px;color:#475467}"
       ".segment{display:grid;grid-template-columns:minmax(260px,.82fr) minmax(360px,1.18fr);gap:18px;padding:18px 0;border-top:1px solid rgba(23,32,51,.12)}.source,.candidate,.empty{background:rgba(255,255,255,.68);border:1px solid rgba(23,32,51,.08);border-radius:18px;padding:18px 20px;box-shadow:0 10px 28px rgba(23,32,51,.04)}.eyebrow{font-size:12px;text-transform:uppercase;letter-spacing:.08em;color:#8a6d3b;margin-bottom:10px}.source p,.candidate p{line-height:1.72;margin:0}.translations{display:grid;gap:12px}.candidate-head{display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:12px}.candidate-head span{font-size:12px;color:#667085;background:#f2f4f7;border-radius:999px;padding:3px 8px}details{margin-top:12px;color:#667085}summary{cursor:pointer}dl{display:grid;grid-template-columns:90px 1fr;gap:6px;font-size:13px}dt{font-weight:700}.empty{color:#667085;font-style:italic}.error{border:1px solid #fda29b;background:#fff1f0;color:#912018;border-radius:14px;padding:12px 14px;margin-bottom:14px}"
       "@media(max-width:900px){.toolbar{grid-template-columns:1fr}.segment{grid-template-columns:1fr}.app{padding:20px 14px 56px}.hero{display:block}}"))

(defn script-json [value]
  ;; Script tags are raw-text elements; escape the only characters that can
  ;; terminate or confuse the tag while preserving valid JSON for textContent.
  (-> (json/generate-string value)
      (str/replace "<" "\\u003c")
      (str/replace ">" "\\u003e")
      (str/replace "&" "\\u0026")))

(defn render-app-html [{:keys [ledger pages selected-slug view filters]}]
  (let [{:keys [page summary segments]} view
        title (or (:title page) "Fluree Translation Viewer")
        boot {:ledger ledger :pages pages :selectedSlug selected-slug :view view}
        signals {:ledger ledger
                 :slug selected-slug
                 :language (:language filters)
                 :model (:model filters)
                 :status (:status filters)}]
    (str "<!doctype html><html lang=\"en\"><head>"
         "<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
         "<title>" (c/html-escape title) " · Translation Viewer</title>"
         "<script type=\"module\" src=\"https://cdn.jsdelivr.net/gh/starfederation/datastar@main/bundles/datastar.js\"></script>"
         "<style>" (app-css) "</style></head>"
         "<body><main class=\"app\" id=\"app\" data-signals='" (c/html-escape (json/generate-string signals)) "'>"
         "<div class=\"hero\"><div><p class=\"subtle\">Fluree · multiple LLM · Datastar · CLJS viewer</p>"
         "<h1 id=\"page-title\">" (c/html-escape title) "</h1>"
         "<p id=\"page-url\" class=\"subtle\">" (c/html-escape (:url page)) "</p>"
         "<div class=\"summary\" id=\"summary\">" (render-summary summary) "</div></div>"
         "<div class=\"subtle\">Queryable translation candidates grouped by source segment.</div></div>"
         "<div id=\"error\"></div>"
         "<form class=\"toolbar\" id=\"toolbar\">"
         "<label>Ledger<input id=\"ledger\" name=\"ledger\" value=\"" (c/html-escape ledger) "\" data-bind-ledger></label>"
         "<label>Page<select id=\"slug\" name=\"slug\" data-bind-slug>" (page-list-options pages selected-slug) "</select></label>"
         "<label>Language<select id=\"language\" name=\"language\" data-bind-language>" (option-list (get-in view [:filters :languages]) (:language filters) "All languages") "</select></label>"
         "<label>Model<select id=\"model\" name=\"model\" data-bind-model>" (option-list (get-in view [:filters :models]) (:model filters) "All models") "</select></label>"
         "<label>Status<select id=\"status\" name=\"status\" data-bind-status>" (option-list (get-in view [:filters :statuses]) (:status filters) "All statuses") "</select></label>"
         "<button id=\"refresh\" type=\"button\">Refresh</button>"
         "</form>"
         "<div id=\"segments\">" (render-segments segments) "</div>"
         "<script id=\"boot-data\" type=\"application/json\">" (script-json boot) "</script>"
         "<script src=\"/assets/viewer.js\"></script>"
         "</main></body></html>")))

(defn initial-state [query]
  (let [ledger (param query :ledger default-ledger)
        pages (data/fetch-pages ledger)
        selected-slug (or (param query :slug nil) (:slug (first pages)))
        filters (filters-from-query query)
        view (if selected-slug
               (data/page-view ledger selected-slug filters)
               {:ledger ledger
                :page {:title "No pages in ledger"}
                :filters {:selected filters :models [] :languages [] :statuses []}
                :summary {:segmentCount 0 :candidateCount 0 :filteredCandidateCount 0 :modelCount 0}
                :segments []})]
    {:ledger ledger :pages pages :selected-slug selected-slug :filters filters :view view}))

(defn list-state [query]
  (let [ledger (param query :ledger default-ledger)]
    {:ledger ledger
     :pages (data/fetch-pages ledger)
     :selected-slug nil
     :filters (filters-from-query query)}))

(defn handle-v1 [request]
  (let [state (initial-state (request-query request))]
    (response 200 "text/html" (render-app-html state))))

(defn handle-list [request]
  (response 200 "text/html" (v2/render-list-html (list-state (request-query request)))))

(defn detail-state [request slug]
  (initial-state (cond-> (request-query request)
                   (not (str/blank? slug)) (assoc :slug slug))))

(defn handle-v2 [request]
  (let [slug (param (request-query request) :slug nil)
        state (detail-state request slug)]
    (if (str/blank? slug)
      (handle-list request)
      (response 200 "text/html" (v2/render-app-html state)))))

(defn handle-detail [request slug]
  (response 200 "text/html" (v2/render-app-html (detail-state request slug))))

(defn handle-pages [request]
  (let [query (request-query request)
        ledger (param query :ledger default-ledger)]
    (json-response {:ledger ledger :pages (data/fetch-pages ledger)})))

(defn handle-page [request]
  (let [query (request-query request)
        ledger (param query :ledger default-ledger)
        slug (param query :slug nil)]
    (if (str/blank? slug)
      (error-response 400 "Missing slug" {:query query})
      (json-response (data/page-view ledger slug (filters-from-query query))))))

(defn serve-file [path content-type]
  (response 200 content-type (slurp path)))

(defn page-slug-from-uri [uri]
  (when (str/starts-with? uri "/page/")
    (decode-url (subs uri (count "/page/")))))

(defn handle [request]
  (try
    (let [uri (:uri request)]
      (cond
        (= uri "/") (handle-list request)
        (= uri "/v1") (handle-v1 request)
        (= uri "/v2") (handle-v2 request)
        (= uri "/viewer-v2") (handle-v2 request)
        (= uri "/api/pages") (handle-pages request)
        (= uri "/api/page") (handle-page request)
        (= uri "/assets/viewer.js") (serve-file "viewer/src/viewer/client.js" "application/javascript")
        (= uri "/assets/viewer.cljs") (serve-file "viewer/src/viewer/client.cljs" "text/plain")
        (= uri "/assets/viewer-v2.js") (serve-file "viewer/src/viewer/viewer-v2.js" "application/javascript")
        (= uri "/assets/viewer-v2.css") (serve-file "viewer/src/viewer/viewer-v2.css" "text/css")
        (= uri "/health") (json-response {:ok true})
        (page-slug-from-uri uri) (handle-detail request (page-slug-from-uri uri))
        :else (not-found)))
    (catch Throwable t
      (error-response 500 (.getMessage t) (or (ex-data t) {})))))

(defn start! [{:keys [port]}]
  (http/run-server #'handle {:port (int port)}))

(defn -main [& args]
  (let [port (parse-long (or (first args) (System/getenv "VIEWER_PORT") (str default-port)))]
    (start! {:port port})
    (println (str "[viewer] http://localhost:" port))
    (println "[viewer] Press Ctrl+C to stop")
    @(promise)))
