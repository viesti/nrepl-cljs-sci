(ns example.core
  (:require [nrepl-cljs-sci.core :as nrepl]))

(defn main [& _args]
  (let [express (js/require "express")
        app (express)
        port 3000
        root (atom {:response "Hello World!"})]
    (.get app "/" (fn [_req res _next]
                    (.send res (:response @root))))
    (.listen app port (fn []
                        (println (str "Example app listening at http://localhost:" port))))
    (nrepl/start-server {:app {:app app
                               :root root}})))
