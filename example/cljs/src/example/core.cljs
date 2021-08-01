(ns example.core
  (:require [nrepl-cljs-sci.core :as nrepl]))

(defn main [& _args]
  (nrepl/start-server {}))
