(ns nrepl-cljs-sci.core
  (:require [net :as node-net]
            [nrepl-cljs-sci.bencode :refer [encode decode-all]]
            [sci.core :as sci]
            [taoensso.timbre :as timbre]
            ["uuid" :as uuid]))

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

(defn eval-ctx-mw [handler]
  (let [last-ns (atom @sci/ns)
        last-error (sci/new-var '*e nil {:ns (sci/create-ns 'clojure.core)})
        ctx (sci/init {:namespaces {'clojure.core {'*e last-error}}
                       :classes {'js goog/global :allow :all}})]
    (fn [request send-fn]
      (handler (assoc request
                      :sci-last-ns last-ns
                      :sci-last-error last-error
                      :sci-ctx ctx)
               send-fn))))

(defn handle-describe [request send-fn]
  (send-fn request {"versions" (js->clj js/process.versions)
                    "aux" {}
                    "ops" {"describe" {}
                           "eval" {}
                           "clone" {}}
                    "status" ["done"]}))

(defn the-sci-ns [ctx ns-sym]
  (sci/eval-form ctx (list 'clojure.core/the-ns (list 'quote ns-sym))))

(defn handle-eval [{:keys [ns code sci-ctx sci-last-ns sci-last-error] :as request} send-fn]
  (sci/binding [sci/ns (or (when ns
                             (the-sci-ns sci-ctx (symbol ns)))
                           @sci/ns)]
    (let [reader (sci/reader code)]
      (try
        (let [next-val (sci/parse-next sci-ctx reader)]
          (when-not (= :sci.core/eof next-val)
            (let[result (sci/eval-form sci-ctx next-val)
                 ns (sci/eval-string* sci-ctx "*ns*")]
              (reset! sci-last-ns ns)
              (send-fn request {"value" (pr-str result)
                                "ns" (str ns)})))
          (send-fn request {"status" ["done"]}))
        (catch :default e
          (sci/alter-var-root sci-last-error (constantly e))
          (send-fn request {"ex" (str e)
                            "ns" (str (sci/eval-string* sci-ctx "*ns*"))
                            "status" ["done"]}))))))

(defn handle-clone [request send-fn]
  (send-fn request {"new-session" (uuid/v4)
                    "status" ["done"]}))

(defn handle-request [{:keys [op] :as request} send-fn]
  (case op
    :describe (handle-describe request send-fn)
    :eval (handle-eval request send-fn)
    :clone (handle-clone request send-fn)
    (do
      (timbre/warn "Unhandled operation" op)
      (send-fn request {"status" ["done"]}))))

(def handler
  (-> handle-request
      coerce-request-mw
      eval-ctx-mw
      log-request-mw))

(defn make-send-fn [socket]
  (fn [_request response]
    (.write socket (encode response))))

(defn make-reponse-handler [socket]
  (-> (make-send-fn socket)
      log-response-mw
      response-for-mw))

(defn on-connect [socket]
  (timbre/debug "Connection accepted")
  (.setNoDelay ^node-net/Socket socket true)
  (let [response-handler (make-reponse-handler socket)]
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
