(ns slip.system
  (:require
   [malli.experimental :as mx]
   [promesa.core :as p]
   [a-frame.interceptor-chain :as ic]
   [slip.kahn :as kahn]
   [slip.data.ref-path :as ref-path]
   [slip.data.refs :as refs]
   [slip.schema :as s]
   [slip.multimethods :as mm]))

(defn ^:private normalise-obj-spec
  "normalize to a KeyedObjectSpec with
   explicit :slip/factory key"
  [k obj-spec]
  (if (some? k)
    (assoc obj-spec :slip/key k)
    obj-spec))

(defn ^:private top-level-refs
  [dspec]
  (let [refs (refs/collect-refs dspec)]
    (->> refs
         (map ref-path/path)
         (map first)
         (into #{}))))

(mx/defn topo-sort-system :- s/VectorSystemSpec
  "given a system-spec returns the system-spec
   topo-sorted by ref dependencies"
  [sys :- s/SystemSpec]

  (let [sys (into
             []
             (for [obj-spec sys]
               (if (map-entry? obj-spec)
                 (let [[k s] obj-spec]
                   (normalise-obj-spec k s))
                 (normalise-obj-spec nil obj-spec))))

        sys-map (into
                 {}
                 (for [{k :slip/key :as obj-spec} sys]
                   [k obj-spec]))

        k-deps (into
                {}
                (for [{k :slip/key
                       dspec :slip/data
                       :as _obj-spec} sys]
                  [k (top-level-refs dspec)]))

        _ (prn k-deps)

        sorted-keys (kahn/kahn-sort k-deps)]

    (into
     []
     (for [k (reverse sorted-keys)]
       (get sys-map k)))))

;; can i create an interceptor chain where
;; the ::enter fn is a start, and the object-spec
;; is provided as enter-data

(def start-object-interceptor
  {::ic/name ::start
   ::ic/enter
   (fn [{:as ctx}
        {k :slip/key
         fk :slip/factory
         d :slip/data
         :as _object-spec}]
     (p/let [obj (mm/start (or fk k) d)]
       (assoc-in ctx [:slip/system k] obj)))})

(ic/register-interceptor
 ::start
 start-object-interceptor)

(mx/defn start-interceptor-chain :- ic/InterceptorContext
  "return an InterceptorContext for starting the system"
  [sys :- s/VectorSystemSpec]
  (let [interceptors (for [object-spec sys]
                       {::ic/key ::start
                        ::ic/enter-data object-spec})]
    (ic/initiate* interceptors)))

(def stop-object-interceptor
  {::ic/name ::start
   ::ic/leave
   (fn [{:as ctx}
        {k :slip/key
         fk :slip/factory
         d :slip/data
         :as _object-spec}]
     (p/let [obj (get-in ctx [:slip/system k])
             _ (mm/stop (or fk k) d obj)]
       (update-in ctx [:slip/system] dissoc k)))})

(ic/register-interceptor
 ::stop
 stop-object-interceptor)

(mx/defn stop-interceptor-chain :- ic/InterceptorContext
  "return an InterceptorContext for stopping the system"
  [sys :- s/VectorSystemSpec]
  (let [interceptors (for [object-spec sys]
                       {::ic/key ::stop
                        ::ic/leave-data object-spec})]
    (ic/initiate* interceptors)))
