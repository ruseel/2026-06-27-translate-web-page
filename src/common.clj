(ns common
  (:require [babashka.fs :as fs]
            [babashka.process :as p]
            [cheshire.core :as json]
            [clojure.string :as str])
  (:import [java.security MessageDigest]))

(def schema "https://schema.org/")
(def base-iri (or (System/getenv "TWP_BASE_IRI") "https://example.org/translate-web-page"))
(def twp (str base-iri "#"))

(def jsonld-context
  {"schema" schema
   "twp" twp
   "WebPage" "schema:WebPage"
   "Paragraph" "schema:Paragraph"
   "TranslatedParagraph" "twp:TranslatedParagraph"
   "url" {"@id" "schema:url"}
   "name" {"@id" "schema:name"}
   "headline" {"@id" "schema:headline"}
   "text" {"@id" "schema:text"}
   "position" {"@id" "schema:position"}
   "isPartOf" {"@id" "schema:isPartOf" "@type" "@id"}
   "hasPart" {"@id" "schema:hasPart" "@type" "@id"}
   "inLanguage" {"@id" "schema:inLanguage"}
   "translationOfWork" {"@id" "schema:translationOfWork" "@type" "@id"}
   "blockKind" {"@id" "twp:blockKind"}
   "sourceUrl" {"@id" "twp:sourceUrl"}
   "slug" {"@id" "twp:slug"}
   "sourceContentHash" {"@id" "twp:sourceContentHash"}
   "defuddleVersion" {"@id" "twp:defuddleVersion"}
   "TranslationRun" "twp:TranslationRun"
   "translationRun" {"@id" "twp:translationRun" "@type" "@id"}
   "llmProvider" {"@id" "twp:llmProvider"}
   "llmModel" {"@id" "twp:llmModel"}
   "thinkingEffort" {"@id" "twp:thinkingEffort"}
   "promptName" {"@id" "twp:promptName"}
   "promptHash" {"@id" "twp:promptHash"}
   "translationStatus" {"@id" "twp:translationStatus"}
   "translatorIdentity" {"@id" "twp:translatorIdentity"}
   "license" {"@id" "schema:license"}
   "generatedAt" {"@id" "schema:dateCreated"}})

(defn ensure-out! []
  (fs/create-dirs "out"))

(defn read-json [path]
  (json/parse-string (slurp path) true))

(defn write-json! [path value]
  (ensure-out!)
  (spit path (json/generate-string value {:pretty true}))
  path)

(defn slugify [s]
  (let [s (or s "article")
        no-scheme (str/replace s #"^[a-zA-Z]+://" "")
        cleaned (-> no-scheme
                    (str/replace #"[^A-Za-z0-9_-]+" "-")
                    (str/replace #"^-+|-+$" ""))
        slug (subs cleaned 0 (min 80 (count cleaned)))]
    (if (str/blank? slug) "article" slug)))

(defn sha256 [s]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") (.getBytes (str s) "UTF-8"))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) digest))))

(defn sh!
  "Run command and return stdout. Throws with stderr on non-zero exit."
  [& args]
  (let [r (apply p/shell {:out :string :err :string} args)]
    (when-not (zero? (:exit r))
      (throw (ex-info (str "Command failed: " (str/join " " args) "\n" (:err r))
                      {:cmd args :result r})))
    (:out r)))

(defn sh
  "Run command and return process map, without throwing."
  [& args]
  (apply p/shell {:out :string :err :string :continue true} args))

(defn fluree-init! []
  (when-not (fs/exists? ".fluree")
    (println "[fluree] init")
    (sh! "fluree" "init")))

(defn ensure-ledger! [ledger]
  (fluree-init!)
  (let [info (sh "fluree" "info" ledger)]
    (when-not (zero? (:exit info))
      (println "[fluree] create" ledger)
      (sh! "fluree" "create" ledger)))
  ledger)

(defn fluree-insert-file! [ledger path]
  (ensure-ledger! ledger)
  (println "[fluree] insert" path "->" ledger)
  (sh! "fluree" "insert" ledger "-f" (str path) "--format" "jsonld"))

(defn fluree-query-json [ledger sparql]
  (ensure-ledger! ledger)
  (let [out (sh! "fluree" "query" ledger "--format" "json" sparql)]
    (json/parse-string out true)))

(defn binding-value [binding k]
  (get-in binding [k :value]))

(defn html-escape [s]
  (str/escape (str (or s "")) {\& "&amp;" \< "&lt;" \> "&gt;" \" "&quot;" \' "&#39;"}))

(defn page-id [slug]
  (str base-iri "/page/" slug))

(defn paragraph-id [slug n]
  (format "%s/paragraph/%s/%04d" base-iri slug (long n)))

(defn translation-id [slug n]
  (format "%s/translation/%s/%04d/ko" base-iri slug (long n)))

(defn translation-run-id [slug]
  (str base-iri "/translation-run/" slug "/ko/" (System/currentTimeMillis)))
