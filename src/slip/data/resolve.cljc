(ns slip.data.resolve
  (:require
   [slip.data.protocols :as p]
   [slip.data.ref-path]
   [slip.data.tag-readers])
  #?(:clj
     (:import
      [clojure.lang IPersistentMap IPersistentVector])))

(declare resolve-data)

(extend-protocol p/IResolveData
  #?@(:clj
      [IPersistentMap
       (-resolve-data [spec sys]
                      ;; (warn "resolve-data MAP")
                      (into
                       {}
                       (for [[k v] spec]
                         (let [rv (resolve-data v sys)]
                           [k rv]))))])

  #?@(:cljs
      [cljs.core.PersistentHashMap
       (-resolve-data [spec sys]
                      (into
                       {}
                       (for [[k v] spec]
                         [k (resolve-data v sys)])))

       cljs.core.PersistentArrayMap
       (-resolve-data [spec sys]
                      (into
                       {}
                       (for [[k v] spec]
                         [k (resolve-data v sys)])))])

  #?(:clj IPersistentVector
     :cljs cljs.core.PersistentVector)
  (-resolve-data [spec sys]
    (mapv
     #(resolve-data % sys)
     spec))

  #?(:clj Object
     :cljs default)
  (-resolve-data [spec _sys]
    ;; (warn "resolve-data DEFAULT" spec)
    spec))

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
                      ;; (warn "resolve-data MAP")
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
    ;; (warn "resolve-data DEFAULT" spec)
    refs))

(defn collect-refs
  "return a set of refs used in the spec"
  ([spec]
   (collect-refs spec #{}))
  ([spec refs]
   (p/-collect-refs spec refs)))

(defn resolve-data
  "resolve data from a spec and a system map"
  [spec sys]
  ;; (warn "resolve-data" spec)
  (p/-resolve-data spec sys))
