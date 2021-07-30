(ns nrepl-cljs-sci.core
  (:require [net :as node-net]
            [nrepl-cljs-sci.bencode :refer [encode decode-all]]
            [sci.core :as sci]
            [taoensso.timbre :as timbre]
            ["uuid" :as uuid]
            [clojure.string :as str]
            [goog.string :as gstr])
  (:require-macros [nrepl-cljs-sci.version :as version]))

(defn response-for-mw [handler]
  (fn [{:keys [id session] :as request} response]
    (let [response (cond-> (assoc response
                                  "id" id)
                     session (assoc "session" session))]
      (handler request response))))

(defn coerce-request-mw [handler]
  (fn [request send-fn]
    (handler (update request :op keyword) send-fn)))

(defn log-request-mw [handler]
  (fn [request send-fn]
    (timbre/debug "request" request)
    (handler request send-fn)))

(defn log-response-mw [handler]
  (fn [request response]
    (timbre/debug "response" response)
    (handler request response)))

;; require is missing from goog.global, so let's expose it
(set! (.-require js/goog.global) js/require)

(defn eval-ctx-mw [handler {:keys [sci-last-error sci-ctx-atom]}]
  (fn [request send-fn]
    (handler (assoc request
                    :sci-last-error sci-last-error
                    :sci-ctx-atom sci-ctx-atom)
             send-fn)))

(defn handle-describe [request send-fn]
  (send-fn request {"versions" (merge (js->clj js/process.versions)
                                      {"nrepl-cljs-sci" (version/get-version)})
                    "aux" {}
                    "ops" {"describe" {}
                           "eval" {}
                           "clone" {}}
                    "status" ["done"]}))

(defn the-sci-ns [ctx ns-sym]
  (sci/eval-form ctx (list 'clojure.core/the-ns (list 'quote ns-sym))))

(defn handle-eval [{:keys [ns code sci-last-error sci-ctx-atom] :as request} send-fn]
  (sci/binding [sci/ns (or (when ns
                             (the-sci-ns @sci-ctx-atom (symbol ns)))
                           @sci/ns)]
    (let [reader (sci/reader code)]
      (try
        (loop [next-val (sci/parse-next @sci-ctx-atom reader)]
          (println "next-val" next-val)
          (when-not (= :sci.core/eof next-val)
            (let[result (sci/eval-form @sci-ctx-atom next-val)
                 ns (sci/eval-string* @sci-ctx-atom "*ns*")]
              (send-fn request {"value" (pr-str result)
                                "ns" (str ns)})
              (recur (sci/parse-next @sci-ctx-atom reader)))))
        (send-fn request {"status" ["done"]})
        (catch :default e
          (sci/alter-var-root sci-last-error (constantly e))
          (send-fn request {"ex" (str e)
                            "ns" (str (sci/eval-string* @sci-ctx-atom "*ns*"))
                            "status" ["done"]}))))))

(defn handle-clone [request send-fn]
  (send-fn request {"new-session" (uuid/v4)
                    "status" ["done"]}))

(defn handle-close [request send-fn]
  (send-fn request {"status" ["done"]}))

(defn handle-request [{:keys [op] :as request} send-fn]
  (case op
    :describe (handle-describe request send-fn)
    :eval (handle-eval request send-fn)
    :clone (handle-clone request send-fn)
    :close (handle-close request send-fn)
    (do
      (timbre/warn "Unhandled operation" op)
      (send-fn request {"status" ["done"]}))))

(defn make-request-handler [opts]
  (-> handle-request
      coerce-request-mw
      (eval-ctx-mw opts)
      log-request-mw))

(defn make-send-fn [socket]
  (fn [_request response]
    (.write socket (encode response))))

(defn make-reponse-handler [socket]
  (-> (make-send-fn socket)
      log-response-mw
      response-for-mw))

(defn on-connect [opts socket]
  (timbre/debug "Connection accepted")
  (.setNoDelay ^node-net/Socket socket true)
  (let [handler (make-request-handler opts)
        response-handler (make-reponse-handler socket)]
    (.on ^node-net/Socket socket "data"
         (fn [data]
           (let [[requests _] (decode-all data :keywordize-keys true)]
             (doseq [request requests]
               (handler request response-handler))))))
  (.on ^node-net/Socket socket "close"
       (fn [had-error?]
         (if had-error?
           (timbre/debug "Connection lost")
           (timbre/debug "Connection closed")))))

(defn load-fn [ctx-atom {:keys [namespace]
                         {:keys [as]} :opts}]
  (when (string? namespace) ;; support cljs string require, e.g. (require '["lib" :as lib])
    (let [ctx @ctx-atom
          ns (js/require namespace)
          ;; Setup aliases to support interop forms, e.g. (lib/fun) and (lib-as/fun)
          new-ctx (sci/merge-opts ctx {:classes {(symbol namespace) ns
                                                 (symbol as) ns}})]
      ;; We make new context visible to evaluation, since changes to :classes current don't reflect in the :env atom in the ctx
      (reset! ctx-atom new-ctx)
      {:file namespace
       :source ""
       ;; We inform SCI to not create a namespace alias
       :omit-as-alias? true})))

(defn start-server [opts]
  (let [{:keys [port log_level ctx]
         :or {port 7080
              log_level "info"} :as _opts} (if (object? opts)
                                             (js->clj opts :keywordize-keys true)
                                             opts)
        sci-last-error (sci/new-var '*e nil {:ns (sci/create-ns 'clojure.core)})
        ctx-atom (atom nil)
        ctx (or ctx
                (sci/init {:namespaces {'clojure.core {'*e sci-last-error}}
                           :classes {'js goog/global
                                     :allow :all}
                           :load-fn (partial load-fn ctx-atom)}))
        server (node-net/createServer (partial on-connect {:sci-last-error sci-last-error
                                                           :sci-ctx-atom ctx-atom}))]
    (reset! ctx-atom ctx)
    (timbre/set-level! (keyword log_level))
    (.listen server
             port
             (fn []
               (timbre/infof "Server started, version %s" (version/get-version))))
    server))

(defn stop-server [server]
  (.close server))
