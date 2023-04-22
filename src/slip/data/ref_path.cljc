(ns slip.data.ref-path
  (:require
   #?(:clj [clojure.pprint :as pprint])
   [slip.data.protocols :as p]
   [taoensso.timbre :refer [info warn]]))

;; don't know why, but cljs compile doesn't agree
;; with deftype here - probably some badly
;; documented interaction with the tag-readers
(defrecord RefPath [path]
  p/IResolveData
  (-resolve-data [_ sys]
    (let [data (get-in sys path)]
      ;; (warn "resolve DataPath" path)
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
    (->RefPath (into [] v))

    (keyword? v)
    (->RefPath [v])

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
     (.write w "#slip.system/ref ")
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
       (write-all writer "#slip.system/ref " (p/-path ref-path) ""))))
