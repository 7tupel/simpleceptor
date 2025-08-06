(ns simpleceptor.core-test
  (:require
   [clojure.edn :as edn]
   [clojure.test :refer [deftest testing is]]
   [simpleceptor.core :refer [intercept i-> routes]]))


;;;; Fixtures and Utilities for the tests

(defn response
  [m]
  {:resp m})

(defn not-edn-error-response
  []
  {:status  400
   :body    "Request does not contain valid edn."})

(def identity-incpt
  "This interceptor does nothing."
  {:key     :identity
   :ingress (fn [ctx] (println "INGRESS") ctx)
   :egress  (fn [ctx] (println "EGRESS") (println ctx) ctx)
   :error   (fn [ex] (println (ex-message ex)) {})})

(def edn-incpt
  "This interceptor decodes and encodes edn."
  {:key     :edn
   :ingress (fn [ctx]
              (when-not (= "text/edn" (get-in ctx [:req :headers "content-type"]))
                (throw (ex-info 
                         (str
                           "Request content-type must be 'text/edn' for the interceptor to work "
                           " but was " (get-in ctx [:req :headers "content-type"]))
                         {:error    ::invalid-content-type
                          :expected "text/edn"
                          :actual   (get-in ctx [:req :headers "content-type"])})))
              (update ctx :req assoc :body (edn/read-string (get-in ctx [:req :body]))))
   :egress  (fn [ctx]
              (update ctx :resp assoc :body (pr-str (get-in ctx [:resp :body]))))
   :error   (fn [ex] (response (not-edn-error-response)))})

(def span-interceptor
  "Add a trace span.
   Adds a span id, span start on ingress and span end on egress."
  {:ingress (fn [ctx]
              (update ctx :trace assoc 
                :span-id (random-uuid) 
                :span-start (str (java.time.LocalDateTime/now))))
   :egress  (fn [ctx]
              (update ctx :trace assoc :span-start (str (java.time.LocalDateTime/now))))
   :error   (fn [ctx] 
              (assoc ctx :error :oh-noes))})


;;;;

(deftest intercept-with-error-test
  (testing "Test the error case using the edn-interceptor."
    (is (=
          @((intercept edn-incpt (fn [_] {:status 200 :body "ok"}))
           {:headers {"content-type" "text/plain"}})
          (not-edn-error-response)))))

(deftest interceptor-threading-test
  (testing "Threading a single interceptor"
    (let [f       (fn [_] {:status 200})
          i-f     (i-> f identity-incpt)
          request {:headers {"content-type" "text/json"}
                   :body    "{}"}]
      (is (= 
            @(i-f request) 
            {:req   {:headers {"content-type" "text/json"}, 
                     :body    "{}"}, 
             :resp  {:status  200}
             :history [[:identity :ingress] [:identity :egress]]}))))
  (testing "Threading a single interceptor that does something"
    (let [f       (fn [req] {:status 200 :body (assoc (:body req) :response [1 2 3])})
          i-f     (i-> f edn-incpt)
          request {:headers {"content-type" "text/edn"}
                   :body    "{:req [:a :b :c]}"}]
      (is (=
            {:req   {:headers {"content-type" "text/edn"}, 
                     :body    {:req [:a :b :c]}}, 
             :resp  {:status 200 :body "{:req [:a :b :c], :response [1 2 3]}"}
             :history [[:edn :ingress] [:edn :egress]]}
            @(i-f request)))))
  (testing "Threading a two interceptors"
    (let [f       (fn [_] {:status 200})
          i-f     (i-> f identity-incpt identity-incpt)
          request {:headers {"content-type" "text/json"}
                   :body    "{}"}]
      (is (= 
            @(i-f request) 
            {:req   {:headers {"content-type" "text/json"}, 
                     :body    "{}"}, 
             :resp  {:status  200}
             :history [[:identity :ingress] [:identity :ingress] 
                       [:identity :egress] [:identity :egress]]})))))


;;; Routing

;; these tests don't test the routing, they only test if the routing
;; handler function is correctly build by the macro
(deftest routes-test-no-interceptor
  (testing "test if the `i->` macro without any interceptors works as expected"
    (let [the-routes (routes "GET /ping" req (fn [_] {:status 200 :body "ok"}) [])
          handler-fn ((get the-routes "GET /ping") {})]
      (is (= (handler-fn {}) {:status 200 :body "ok"})))))
  
(deftest routes-test-no-interceptor-position-argument
  (testing "test if the `i->` macro without any interceptors works as expected"
    (let [the-routes (routes "GET /ping" [id] (fn [_] {:status 200 :body "ok"}) [])
          handler-fn ((get the-routes "GET /ping") {})]
      (is (= (handler-fn {:path-params [23]}) {:status 200 :body "ok"})))))

(deftest routes-test-identity-interceptor
  (testing "test if the `i->` macro with any interceptors works as expected"
    (let [the-routes (routes "GET /ping" req (fn [_] {:status 200 :body "ok"}) [identity-incpt])
          request    {:headers {"content-type" "text/json"} :body "{}"}
          handler-fn (get the-routes "GET /ping")]
      (is (= 
            @(handler-fn request)
            {:status 200 :body "ok"})))))

(deftest routes-test-identity-interceptor-position-argument
  (testing "test if the `i->` macro with any interceptors works as expected"
    (let [the-routes (routes "GET /ping/*" [id] (fn [req] (println "ID: " id) {:status 200 :body (str id)}) [identity-incpt])
          request    {:headers {"content-type" "text/json"} :body "{}" :path-params [345]}
          handler-fn (get the-routes "GET /ping/*")]
      (is (= 
            @(handler-fn request)
            {:status 200 :body "345"})))))

(deftest incept-threading-test-edn-interceptor
  (testing "test if the `i->` macro with any interceptors works as expected"
    (let [the-routes (routes "GET /edn" req 
                       (fn [req] 
                         {:status 200 
                          :body   (assoc (:body req) :response [1 2 3])}) 
                       [edn-incpt])
          request    {:headers {"content-type" "text/edn"}
                      :body    "{:req [:a :b :c]}"}
          handler-fn (get the-routes "GET /edn")]
      (is (= 
            @(handler-fn request)
            {:status 200 :body "{:req [:a :b :c], :response [1 2 3]}"})))))


(deftest incept-threading-test-edn-interceptor-fail
  (testing "test if the `i->` macro with any interceptors works as expected"
    (let [the-routes (routes "GET /edn" req 
                       (fn [req] 
                         {:status 200 
                          :body   (assoc (:body req) :response [1 2 3])}) 
                       [edn-incpt])
          request    {:headers {"content-type" "text/json"}
                      :body    "{:req [:a :b :c]}"}
          handler-fn (get the-routes "GET /edn")]
      (is (= 
            @(handler-fn request)
            (not-edn-error-response))))))