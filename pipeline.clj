(ns pipeline
  (:require [cheshire.core :as json]
            [fetch.core :as fetch]
            [translate.core :as translate]
            [genhtml.core :as genhtml]))

(defn run!
  "Run fetch -> translate -> genhtml through Fluree.

  Usage:
    bb page <url-or-defuddle-json> [ledger]

  This is the real pipeline: translation calls Pi by default. For local plumbing
  tests only, set MOCK_TRANSLATION=1."
  [{:keys [input ledger]}]
  (let [source (or input (throw (ex-info "Usage: bb page <url-or-defuddle-json> [ledger]" {})))
        ledger (or ledger "translate")
        fetched (fetch/fetch->fluree! {:input source :ledger ledger})
        translated (translate/translate->fluree! (select-keys fetched [:ledger :slug :page-id]))
        generated (genhtml/genhtml! (select-keys fetched [:ledger :slug :page-id]))]
    {:fetch fetched
     :translate translated
     :genhtml generated}))

(defn -main [& args]
  (let [input (first args)
        ledger (second args)
        result (run! {:input input :ledger ledger})]
    (println "\nPipeline complete:")
    (println (json/generate-string result {:pretty true}))))
