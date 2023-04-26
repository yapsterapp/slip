(ns slip.data.ref-path
  (:require
   #?(:clj [clojure.pprint :as pprint])
   [slip.data.protocols :as p]
   [a-frame.interceptor-chain.data.protocols :as ic.d.p]
   [taoensso.timbre :refer [info warn]]))

;; don't know why, but cljs compile doesn't agree
;; with deftype here - perhaps some
;; interaction with the tag-readers
(defrecord RefPath [path maybe?]
  ic.d.p/IResolveData
  (-resolve-data [_spec interceptor-ctx]
    (let [data (get-in
                interceptor-ctx
                (into [:slip/system] path))]

      (when (and (not maybe?)
                 (nil? data))
        (throw
         (ex-info "nil ref" {:path path
                             :system (get interceptor-ctx :slip/system)})))
      data))

  p/ICollectRefs
  (-collect-refs [spec refs]
    (conj refs spec))

  p/IRefPath
  (-path [_] path))

(defn ref-path
  [v]
  (cond
    (sequential? v)
    (->RefPath (into [] v) false)

    (keyword? v)
    (->RefPath [v] false)

    :else
    (throw (ex-info "unknown RefPath" {:ref-path v}))))

(defn maybe-ref-path
  [v]
  (cond
    (sequential? v)
    (->RefPath (into [] v) true)

    (keyword? v)
    (->RefPath [v] true)

    :else
    (throw (ex-info "unknown RefPath" {:ref-path v}))))

(defn ref-path?
  [o]
  (instance? RefPath o))

(defn path
  [o]
  (p/-path o))

#?(:clj
   (defn print-ref-path
     [ref-path ^java.io.Writer w]
     (.write w "#slip/ref ")
     (print-method (p/-path ref-path) w)))

#?(:clj
   (defmethod print-method RefPath [this ^java.io.Writer w]
     (print-ref-path this w)))

#?(:clj
   (defmethod print-dup RefPath [this ^java.io.Writer w]
     (print-ref-path this w)))

#?(:clj
   (.addMethod pprint/simple-dispatch
               RefPath
               (fn [ref-path]
                 (print-ref-path ref-path *out*))))

#?(:cljs
   (extend-protocol IPrintWithWriter
     RefPath
     (-pr-writer [ref-path writer _]
       (write-all writer "#slip/ref " (p/-path ref-path) ""))))
