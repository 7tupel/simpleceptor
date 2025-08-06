(ns simpleceptor.core
  (:require
   [manifold.deferred :as d]))


(defn intercept
  [icpt f]
  (fn [ctx]
    (if-not (some? (:error ctx))
      (-> ctx
       (d/chain 
         #(-> % ((:ingress icpt)) (update :history conj [(:key icpt) :ingress])) 
         f 
         #(-> % ((:egress icpt)) (update :history conj [(:key icpt) :egress])))
       (d/catch Exception #((:error icpt) %)))
      ctx)))

(defmacro i->
  [h & interceptors]
  (let [ctx   (gensym)
        form  (loop [x `(fn [~ctx] (assoc ~ctx :resp (~h (:req ~ctx))))
                     interceptors (reverse interceptors)]
                (if interceptors
                  (let [incpt (first interceptors)
                        threaded `(intercept ~incpt ~x)]
                    (recur threaded (next interceptors)))
                  x))]
    `(fn [~ctx] (~form {:req ~ctx :history []}))))

(defmacro routes
  [& body]
  (let [req-sym 'req]
    (into {}
      (for [[path params handler interceptors] (partition 4 body)]
        [path `(fn [~req-sym]
                 (let [~params ~(if (vector? params)
                                  `(:path-params ~req-sym)
                                  req-sym)]
                   ~(if (empty? interceptors)
                      handler
                      (list `d/chain (list (concat `(i-> ~handler) interceptors) req-sym) :resp))))]))))