(ns nrepl-cljs-sci.version
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

(defmacro get-version []
  (if-let [version-file (io/resource "nrepl-cljs-sci-version.edn")]
    ;; Running in jar
    (-> version-file slurp edn/read-string :version)
    ;; Running locally
    (if-let [version-clj-file (io/resource "nrepl_cljs_sci/version.clj")]
      (let [package-json (-> version-clj-file
                             (.getPath)
                             (io/file)
                             (.getParentFile)
                             (.getParentFile)
                             (.getParentFile)
                             (io/file "package.json")
                             )]
        (-> package-json
            slurp
            (json/read-str :key-fn keyword)
            :version))
      ;; Should not end up here
      "")))
