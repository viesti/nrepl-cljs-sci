(ns nrepl-cljs-sci.core
  (:require [net :as node-net]
            [nrepl-cljs-sci.bencode :refer [encode decode-all]]
            [sci.core :as sci]
            [taoensso.timbre :as timbre]
            ["uuid" :as uuid]))

(defn response-for-mw [handler]
  (fn [{:keys [id session] :as request}]
    (let [response (handler request)]
      (cond-> (assoc response
                     "id" id)
        session (assoc "session" session)))))

(defn coerce-request-mw [handler]
  (fn [request]
    (handler (update request :op keyword))))

(defn log-mw [handler]
  (fn [request]
    (let [response (handler request)]
      (timbre/debug "request" request)
      (timbre/debug "response" response)
      response)))

(defn eval-ctx-mw [handler]
  (let [last-ns (atom @sci/ns)
        last-error (sci/new-var '*e nil {:ns (sci/create-ns 'clojure.core)})
        ctx (sci/init {:namespaces {'clojure.core {'*e last-error}}
                       :classes {'js goog/global :allow :all}})]
    (fn [request]
      (handler (assoc request
                      :sci-last-ns last-ns
                      :sci-last-error last-error
                      :sci-ctx ctx)))))

(defn handle-describe []
  {"versions" (js->clj js/process.versions)
   "aux" {}
   "ops" {"describe" {}
          "eval" {}
          "clone" {}}
   "status" ["done"]})

(defn handle-eval [{:keys [ns code sci-ctx sci-last-ns sci-last-error]}]
  (sci/binding [sci/ns (or (when ns
                             (symbol ns))
                           @sci/ns)]
    (let [reader (sci/reader code)
          next-val (sci/parse-next sci-ctx reader)
          result (if (= :sci.core/eof next-val)
                   {"status" ["done"]}
                   (try
                     {"value" (pr-str (js->clj (sci/eval-form sci-ctx next-val)))
                      "status" ["done"]}
                     (catch :default e
                       (sci/alter-var-root sci-last-error (constantly e))
                       {"ex" (str e)
                        "status" ["done"]})))
          ns (sci/eval-string* sci-ctx "*ns*")]
      (reset! sci-last-ns ns)
      (assoc result
             "ns" (str ns)))))

(defn handle-clone []
  {"new-session" (uuid/v4)
   "status" ["done"]})

(defn handle-request [{:keys [op] :as request}]
  (case op
    :describe (handle-describe)
    :eval (handle-eval request)
    :clone (handle-clone)
    (do
      (timbre/warn "Unhandled operation" op)
      {"status" ["done"]})))

(def handler
  (-> handle-request
      coerce-request-mw
      eval-ctx-mw
      response-for-mw
      log-mw))

(defn on-connect [socket]
  (timbre/debug "Connection accepted")
  (.setNoDelay ^node-net/Socket socket true)
    (.on ^node-net/Socket socket "data"
         (fn [data]
           (let [[requests _] (decode-all data :keywordize-keys true)]
             (doseq [request requests]
             (.write socket (encode (handler request)))))))
  (.on ^node-net/Socket socket "close"
       (fn [had-error?]
         (if had-error?
           (timbre/debug "Connection lost")
           (timbre/debug "Connection closed")))))

(defn start-server [opts]
  (let [{:keys [port log_level] :or {port 7080
                                     log_level "info"} :as _opts} (if (object? opts)
                                                                    (js->clj opts :keywordize-keys true)
                                                                    opts)
        server (node-net/createServer on-connect)]
    (timbre/set-level! (keyword log_level))
    (.listen server
             port
             (fn []
               (timbre/info "Server started")))
    server))

(defn stop-server [server]
  (.close server))
