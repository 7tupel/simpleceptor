(ns example
  (:require
   [aleph.netty :as netty]
   [aleph.http :as http]
   [manifold.deferred :as d]
   [byte-streams :as bs]
   [clj-simple-router.core :as sr]
   [simpleceptor.core :as sc]))


(def config
  "Application configuration."
  {:http-server {:port 9797}})

(defn response
  [m]
  {:resp m})

(defn echo-handler
  [req]
  (println "Echo Handler")
  {:status  200
   :body    (:body req)})

(defn delayed-handler 
  [req]
  (d/future
    (Thread/sleep 2000)
    {:status 200
     :body "DELAYED"}))

(defn ping-handler
  [req]
  {:status 200 
   :body "PONG"})

(defn edn-handler
  [req]
  {:status 200 
   :body (conj (:body req) [:a {"key" "value"}])})

(defn identity-handler
  [id req]
  {:status 200 
   :body (str "Identity: " id)})


(defn inc-handler
  [req]
  {:status 200 
   :body (inc (:body req))})

(def identity-incpt
  "This interceptor does nothing."
  {:key     :identity
   :ingress (fn [ctx] ctx)
   :egress  (fn [ctx] ctx)
   :error   (fn [ex] (println (ex-message ex)) {})})

(def peek-incpt
  ""
  {:key     :peek
   :ingress (fn [ctx] (println "|INGRESS|") (clojure.pprint/pprint ctx) (println "|INGRESS|") ctx)
   :egress  (fn [ctx] (println "|EGRESS|") (clojure.pprint/pprint ctx) (println "|EGRESS|") ctx)
   :error   (fn [ex] (println "Oh NOES!") (println (ex-message ex)) {})})

(def tf-edn-incpt
  "This interceptor transforms the body of the request from edn text
   to clojure collections on ingress. On egress, the clojure collection
   present in the body is serialized into edn compliant text."
  {:key     :transform-edn
   :ingress (fn [ctx] 
              (let [content-type (get-in ctx [:req :headers "content-type"])]
                (when-not (= "application/edn" content-type) 
                  (throw (ex-info "Invalid content-type!" {:expected "application/edn" :actual content-type})))
                (update ctx :req assoc :body (clojure.edn/read-string (bs/to-string (get-in ctx [:req :body]))))))
   :egress  (fn [ctx]
              (update ctx :resp assoc :body (pr-str (get-in ctx [:resp :body]))))
   :error   (fn [ex] 
              (println "Unable to parse request body as edn text.") 
              (println (ex-message ex)) 
              (response 
                {:status 400
                 :body (print-str (ex-data ex))}))})


(def inc-incpt
  {:key     :identity
   :ingress (fn [ctx] 
              (assoc-in ctx [:req :body] (-> ctx :req :body bs/to-string parse-long inc)))
   :egress  (fn [ctx] 
              (update ctx :resp assoc :body (-> ctx :resp :body inc str)))
   :error   (fn [ex] 
              (println (ex-message ex)) 
              (response {:status 500 :body (ex-message ex)}))})

(def routes
  (sc/routes
    "POST /echo" req echo-handler [peek-incpt]
    
    "GET /delayed" req delayed-handler []
    
    "GET /ping" req  ping-handler [identity-incpt]
    
    "GET /identity/*" [id] (identity-handler id req) []
    
    "POST /edn" req edn-handler [tf-edn-incpt]
    
    "POST /inc" req inc-handler  [inc-incpt]
    ))

(defn handler []
  (sr/router 
    routes))

(defonce server (atom nil))

(defn stop-server!
  []
  (when (some? @server)
    (do
      (.close @server)
      (netty/wait-for-close @server)
      (reset! server nil))))


(def http-server-default-config
  {:shutdown-quiet-period 1
   :shutdown-timeout      2
   :raw-stream?           true})


(defn start-server!
  []
  (when (nil? @server)
    (reset! server 
      (http/start-server (handler) (merge http-server-default-config (:http-server config))))))


(comment
  
  (start-server!)

  (stop-server!)

  @server
  
  (-> @(http/post "http://localhost:9797/echo" {:body "hello world"}) :body bs/to-string)
  
  (-> @(http/get "http://localhost:9797/ping") :body bs/to-string)
  
  (-> @(http/get "http://localhost:9797/delayed") :body bs/to-string)
  
  (-> @(http/get "http://localhost:9797/identity/2") :body bs/to-string)
  
  (-> @(http/get "http://localhost:9797/identity/2?arg=value") :body bs/to-string)
  
  (-> @(http/post 
         "http://localhost:9797/edn"
         {:headers {"content-type" "application/edn"}
          :body    "[:a 2 {:my/key \"the value is great\"}]"})
    :body 
    bs/to-string)
  
    (-> @(http/post 
         "http://localhost:9797/edn"
         {:body    "[:a 2]"})
    :body
    bs/to-string
    )
  
  (-> @(http/post 
         "http://localhost:9797/inc"
         {:body    "1"})
    :body 
    bs/to-string)
)
