(ns viewer.v2
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [common :as c])
  (:import [java.net URLEncoder]
           [java.nio.charset StandardCharsets]))

(defn script-json [value]
  ;; Script tags are raw-text elements; escape characters that can terminate or
  ;; confuse the tag while preserving valid JSON for textContent.
  (-> (json/generate-string value)
      (str/replace "<" "\\u003c")
      (str/replace ">" "\\u003e")
      (str/replace "&" "\\u0026")))

(def globe-icon
  (str "<svg class=\"v2-brand-mark\" viewBox=\"0 0 24 24\" aria-hidden=\"true\">"
       "<circle cx=\"12\" cy=\"12\" r=\"10\"></circle>"
       "<path d=\"M2 12h20\"></path>"
       "<path d=\"M12 2a15.3 15.3 0 0 1 0 20\"></path>"
       "<path d=\"M12 2a15.3 15.3 0 0 0 0 20\"></path>"
       "</svg>"))

(defn icon [name body]
  (str "<svg viewBox=\"0 0 24 24\" aria-hidden=\"true\" class=\"v2-icon-" name "\">" body "</svg>"))

(def columns-icon
  (icon "columns" "<rect x=\"3\" y=\"4\" width=\"7\" height=\"16\" rx=\"1\"></rect><rect x=\"14\" y=\"4\" width=\"7\" height=\"16\" rx=\"1\"></rect>"))

(def stack-icon
  (icon "stack" "<path d=\"M4 6h16\"></path><path d=\"M4 12h16\"></path><path d=\"M4 18h16\"></path>"))

(def type-icon
  (icon "type" "<path d=\"M4 7V4h16v3\"></path><path d=\"M9 20h6\"></path><path d=\"M12 4v16\"></path>"))

(def sun-icon
  (icon "sun" "<circle cx=\"12\" cy=\"12\" r=\"4\"></circle><path d=\"M12 2v2\"></path><path d=\"M12 20v2\"></path><path d=\"m4.93 4.93 1.41 1.41\"></path><path d=\"m17.66 17.66 1.41 1.41\"></path><path d=\"M2 12h2\"></path><path d=\"M20 12h2\"></path><path d=\"m6.34 17.66-1.41 1.41\"></path><path d=\"m19.07 4.93-1.41 1.41\"></path>"))

(def coffee-icon
  (icon "coffee" "<path d=\"M10 2v2\"></path><path d=\"M14 2v2\"></path><path d=\"M16 8a1 1 0 0 1 1 1v8a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4V9a1 1 0 0 1 1-1h12Z\"></path><path d=\"M17 8h1a4 4 0 0 1 0 8h-1\"></path><path d=\"M6 2v2\"></path>"))

(def moon-icon
  (icon "moon" "<path d=\"M20.98 11.62A9 9 0 1 1 12.38 3a7 7 0 0 0 8.6 8.62Z\"></path>"))

(defn encode-url [s]
  (URLEncoder/encode (str (or s "")) (.name StandardCharsets/UTF_8)))

(defn page-href [ledger slug]
  (str "/page/" (encode-url slug)
       (when-not (str/blank? ledger)
         (str "?ledger=" (encode-url ledger)))))

(defn hostname [url]
  (try
    (some-> url java.net.URI. .getHost (str/replace #"^www\." ""))
    (catch Throwable _ nil)))

(defn short-hash [hash]
  (when-not (str/blank? hash)
    (subs hash 0 (min 12 (count hash)))))

(defn render-page-card [ledger {:keys [slug title url sourceContentHash creationDate]}]
  (str "<a class=\"v2-page-card\" href=\"" (c/html-escape (page-href ledger slug)) "\">"
       "<span class=\"v2-card-kicker\">Translated page</span>"
       "<h2>" (c/html-escape (or title slug "Untitled")) "</h2>"
       "<p class=\"v2-card-url\">" (c/html-escape (or (hostname url) url "No source URL")) "</p>"
       "<div class=\"v2-card-meta\">"
       (when-not (str/blank? creationDate)
         (str "<span>" (c/html-escape creationDate) "</span>"))
       (when-not (str/blank? sourceContentHash)
         (str "<span>hash " (c/html-escape (short-hash sourceContentHash)) "</span>"))
       "<span>Read translation</span>"
       "</div>"
       "</a>"))

(defn render-list-html [{:keys [ledger pages]}]
  (str "<!doctype html><html lang=\"en\"><head>"
       "<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
       "<title>Good Writes - Translation Archive</title>"
       "<link rel=\"stylesheet\" href=\"/assets/viewer-v2.css\">"
       "</head><body>"
       "<div class=\"v2-shell\" id=\"app\">"
       "<nav class=\"v2-nav\" aria-label=\"Reader navigation\"><div class=\"v2-nav-inner\">"
       "<a class=\"v2-brand\" href=\"/\">" globe-icon "<span class=\"v2-brand-text\">Good Writes</span></a>"
       "<div class=\"v2-nav-meta\">" (count pages) " translated pages</div>"
       "</div></nav>"
       "<main class=\"v2-main v2-list-main\">"
       "<header class=\"v2-list-hero\">"
       "<p class=\"v2-eyebrow\">Fluree · LLM translations · bilingual reader</p>"
       "<h1>Good writing, translated for deep reading.</h1>"
       "<p>Browse imported web pages and open a distraction-free side-by-side English/Korean reader.</p>"
       "<form class=\"v2-ledger-form\" method=\"get\" action=\"/\">"
       "<label>Ledger <input name=\"ledger\" value=\"" (c/html-escape ledger) "\"></label>"
       "<button type=\"submit\">Load</button>"
       "</form>"
       "</header>"
       (if (seq pages)
         (str "<section class=\"v2-page-grid\" aria-label=\"Translated pages\">"
              (apply str (map #(render-page-card ledger %) pages))
              "</section>")
         "<section class=\"v2-empty-state\"><h2>No translated pages yet.</h2><p>Fetch and translate a page, then come back here to read it.</p></section>")
       "</main></div>"
       "</body></html>"))

(defn render-app-html [{:keys [ledger pages selected-slug view]}]
  (let [{:keys [page]} view
        title (or (:title page) "Translation Reader")
        boot {:ledger ledger :pages pages :selectedSlug selected-slug :view view}
        url (:url page)]
    (str "<!doctype html><html lang=\"en\"><head>"
         "<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
         "<title>" (c/html-escape title) " - Translation Reader</title>"
         "<link rel=\"stylesheet\" href=\"/assets/viewer-v2.css\">"
         "</head><body>"
         "<div class=\"v2-progress\" id=\"progress\"></div>"
         "<div class=\"v2-shell\" id=\"app\">"
         "<nav class=\"v2-nav\" aria-label=\"Reader navigation\"><div class=\"v2-nav-inner\">"
         "<a class=\"v2-brand\" href=\"/\">" globe-icon "<span class=\"v2-brand-text\">Good Writes</span></a>"
         "<div class=\"v2-nav-actions\"><a class=\"v2-nav-link\" href=\"/\">All pages</a><div class=\"v2-nav-meta\" id=\"nav-meta\">" (c/html-escape (or url title)) "</div></div>"
         "</div></nav>"
         "<main class=\"v2-main\" id=\"reader-main\">"
         "<header class=\"v2-header\">"
         "<h1 id=\"page-title\">" (c/html-escape title) "</h1>"
         "<a id=\"page-url\" class=\"v2-url\" href=\"" (c/html-escape (or url "#")) "\""
         (when (str/blank? url) " hidden")
         ">" (c/html-escape url) "</a>"
         "</header>"
         "<div id=\"error\"></div>"
         "<article class=\"v2-reader\" id=\"segments\" aria-live=\"polite\"></article>"
         "</main></div>"
         "<div class=\"v2-controls\" id=\"reader-controls\" aria-label=\"Reader controls\">"
         "<div class=\"v2-control-group\" role=\"group\" aria-label=\"Layout\">"
         "<button type=\"button\" data-layout=\"side-by-side\" title=\"Side-by-side\">" columns-icon "</button>"
         "<button type=\"button\" data-layout=\"stacked\" title=\"Stacked\">" stack-icon "</button>"
         "<button type=\"button\" data-layout=\"en-only\" title=\"English only\">EN</button>"
         "<button type=\"button\" data-layout=\"ko-only\" title=\"Korean only\">KO</button>"
         "</div>"
         "<div class=\"v2-control-group\" role=\"group\" aria-label=\"Text size\">"
         "<button type=\"button\" class=\"v2-size-small\" id=\"size-down\" title=\"Decrease text size\">" type-icon "</button>"
         "<span id=\"text-size\" aria-live=\"polite\">20</span>"
         "<button type=\"button\" class=\"v2-size-large\" id=\"size-up\" title=\"Increase text size\">" type-icon "</button>"
         "</div>"
         "<div class=\"v2-control-group\" role=\"group\" aria-label=\"Theme\">"
         "<button type=\"button\" data-theme=\"light\" title=\"Light theme\">" sun-icon "</button>"
         "<button type=\"button\" data-theme=\"sepia\" title=\"Sepia theme\">" coffee-icon "</button>"
         "<button type=\"button\" data-theme=\"dark\" title=\"Dark theme\">" moon-icon "</button>"
         "</div>"
         "</div>"
         "<script id=\"boot-data\" type=\"application/json\">" (script-json boot) "</script>"
         "<script src=\"/assets/viewer-v2.js\"></script>"
         "</body></html>")))
