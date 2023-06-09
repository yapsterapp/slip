(ns slip.interceptors
  (:require
   [malli.experimental :as mx]
   [promesa.core :as p]
   [a-frame.interceptor-chain :as ic]
   [a-frame.std-interceptors :as std-interceptors]
   [a-frame.interceptor-chain.schema :as ic.schema]
   [a-frame.interceptor-chain.data :as ic.data]
   [slip.kahn :as kahn]
   [slip.data.ref-path :as ref-path]
   [slip.data.refs :as refs]
   [slip.schema :as s]
   [slip.multimethods :as mm]
   [taoensso.timbre :refer [debug info warn]]))

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
   topo-sorted by ref dependencies - objects
   with no deps first"
  [sys-spec :- s/SystemSpec]

  (let [sys-spec (into
                  []
                  (for [obj-spec sys-spec]
                    (if (map-entry? obj-spec)
                      (let [[k s] obj-spec]
                        (normalise-obj-spec k s))
                      (normalise-obj-spec nil obj-spec))))

        sys-map (into
                 {}
                 (for [{k :slip/key :as obj-spec} sys-spec]
                   [k obj-spec]))

        k-deps (into
                {}
                (for [{k :slip/key
                       dspec :slip/data
                       :as _obj-spec} sys-spec]
                  [k (top-level-refs dspec)]))

        sorted-keys (->> k-deps
                         (kahn/kahn-sort)
                         reverse)]

    ;; (prn "topo-sort-system" k-deps sorted-keys)

    (filterv
     some? ;; assuming bad refs will be resolved from config/init
     (for [k sorted-keys]
       (get sys-map k)))))

(def start-object-interceptor
  "an interceptor to start an object"
  {::ic/name ::start
   ::ic/enter
   (fn [{:as ctx}
        {{k :slip/key
          fk :slip/factory
          data-spec :slip/data
          :as _object-spec} ::object
         :as _interceptor-spec}]

     (let [resolved-data (ic.data/resolve-data data-spec ctx)]

       (debug "start:"
              _object-spec
              {:slip/key k
               :slip/factory (or fk k)
               :slip/data data-spec
               :slip/resolved-data resolved-data})

       (p/let [obj (mm/start (or fk k) resolved-data)]
         [(assoc-in ctx [:slip/system k] obj)
          resolved-data])))

   ::ic/error
   (fn [{stack ::ic/stack
         :as _ctx}
        _object-spec
        err]
     (let [{{_k :slip/key
             _fk :slip/factory
             _d :slip/data
             :as object-spec} ::object
            :as _start-int-spec} (peek stack)

           org-err (ic/unwrap-original-error err)]

       (warn "start error: unwinding"
             {:slip/object-spec object-spec
              :slip/error-message (ex-message org-err)
              :slip/error-data (ex-data org-err)})

       ;; continue unwinding
       (throw (ic/rethrow err))))})

(ic/register-interceptor
 ::start
 start-object-interceptor)

(mx/defn start-interceptor-chain :- ic.schema/InterceptorContext
  "return an
   [InterceptorContext](https://cljdoc.org/d/com.github.yapsterapp/a-frame/CURRENT/api/a-frame.interceptor-chain.schema#InterceptorContext)
   interceptor chain description, for starting a system"
  [sys :- s/VectorSystemSpec]
  (let [interceptors (for [object-spec sys]
                       {::ic/key ::start
                        ::object object-spec})]
    (ic/initiate*
     (into
      [::std-interceptors/unhandled-error-report]
      interceptors))))

(def stop-object-interceptor
  "an interceptor to stop an object"
  {::ic/name ::stop

   ::ic/leave
   (fn [{:as ctx}
        {{k :slip/key
          fk :slip/factory
          data-spec :slip/data
          :as object-spec} ::object
         :as _interceptor-spec}]

     (p/let [obj (get-in ctx [:slip/system k])
             resolved-data (ic.data/resolve-data data-spec ctx)
             _ (debug "stop:"
                      {:slip/key k
                       :slip/factory (or fk k)
                       :slip/data data-spec
                       :slip/resolved-data resolved-data
                       :slip/object obj})
             _ (mm/stop (or fk k) resolved-data obj)]

       (when (nil? obj)
         (throw
          (ex-info "no object to stop in system"
                   {:slip/object-spec object-spec
                    :slip/system (get ctx :slip/system)})))

       (update-in ctx [:slip/system] dissoc k)))

   ::ic/error
   (fn [{stack ::ic/stack
         :as ctx}
        _object-spec
        err]
     (let [{{_k :slip/key
             _fk :slip/factory
             _d :slip/data
             :as object-spec} ::object
            :as _start-int-spec} (peek stack)

           org-err (ic/unwrap-original-error err)]

       (warn
        "stop error:"
        {:slip/object-spec object-spec
         :slip/error-message (ex-message org-err)
         :slip/error-data (ex-data org-err)})

       ;; do not remove the failed stop object from the system
       ;; so it's available for inspection
       ctx))})

(ic/register-interceptor
 ::stop
 stop-object-interceptor)

(mx/defn stop-interceptor-chain :- ic.schema/InterceptorContext
  "return an
   [InterceptorContext](https://cljdoc.org/d/com.github.yapsterapp/a-frame/CURRENT/api/a-frame.interceptor-chain.schema#InterceptorContext)
   interceptor chain description, for stopping a system"
  [sys-spec :- s/VectorSystemSpec]
  (let [interceptors (for [object-spec sys-spec]
                       {::ic/key ::stop
                        ::object object-spec})]
    (ic/initiate*
     (into
      [::std-interceptors/unhandled-error-report]
      interceptors))))
