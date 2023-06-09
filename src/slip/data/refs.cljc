(ns slip.data.refs
  (:require
   [slip.data.protocols :as p]
   [slip.data.ref-path]
   [slip.data.tag-readers])
  #?(:clj
     (:import
      [clojure.lang IPersistentMap IPersistentVector])))

(declare collect-refs)

(defn ^:private collect-map-spec-refs
  [map-spec refs]
  (reduce
   (fn [rs sp]
     (collect-refs sp rs))
   refs
   (vals map-spec)))

(defn ^:private collect-vector-spec-refs
  [vec-spec refs]
  (reduce
   (fn [rs sp]
     (collect-refs sp rs))
   refs
   vec-spec))

(extend-protocol p/ICollectRefs
  #?@(:clj
      [IPersistentMap
       (-collect-refs [spec refs]
                      (collect-map-spec-refs spec refs))])

  #?@(:cljs
      [cljs.core.PersistentHashMap
       (-collect-refs [spec refs]
                      (collect-map-spec-refs spec refs))

       cljs.core.PersistentArrayMap
       (-collect-refs [spec refs]
                      (collect-map-spec-refs spec refs))])

  #?(:clj IPersistentVector
     :cljs cljs.core.PersistentVector)
  (-collect-refs [spec refs]
    (collect-vector-spec-refs spec refs))

  #?(:clj Object
     :cljs default)
  (-collect-refs [_spec refs]
    refs))

(defn collect-refs
  "return a set of refs used in the spec"
  ([spec]
   (collect-refs spec #{}))
  ([spec refs]
   (p/-collect-refs spec refs)))
