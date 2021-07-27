(ns nrepl-cljs-sci.version
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defmacro get-version []
  (-> (io/file "package.json")
      slurp
      (json/read-str :key-fn keyword)
      :version))
