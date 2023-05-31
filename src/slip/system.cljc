(ns slip.system
  "a container for a stateful system of objects,
   and the specification for the system.

   both the system and the specification can be updated.
   the system is lazily created, but is safe for access and
   update in multi-threaded scenarios"
  (:require
   [promesa.core :as p]
   [taoensso.timbre :refer [error]]
   [slip.protocols :as pt]
   [slip.system-map :as system-map]))

(defrecord SlipSystem [label system-spec-a system-map-p-container]
  pt/ISlipSystem
  (-spec [_] @system-spec-a)

  (-reset-spec! [_ system-spec]
    (reset! system-spec-a system-spec))

  (-system-map [_]
    @system-map-p-container)

  (-start! [_ opts resolve-fn reject-fn]
    (#?(:clj send :cljs swap!)
     system-map-p-container
     (fn [system-map-p]

       (let [started-system-map-p (p/then
                                   system-map-p
                                   #(system-map/start! % opts))]

         ;; return a result to the caller
         (p/handle
          started-system-map-p
          (fn [succ err]
            (if (some? err)
              (reject-fn err)
              (resolve-fn
               (system-map/dissoc-impl-keys succ)))))

         ;; update the container value
         started-system-map-p))))

  (-stop! [_ opts resolve-fn reject-fn]
    (#?(:clj send :cljs swap!)
     system-map-p-container
     (fn [system-map-p]

       (let [stopped-system-map-p (p/then
                                   system-map-p
                                   #(system-map/stop! % opts))

             ;; if the system stopped cleanly, then
             ;; reinitialise it with the potentially updated spec.
             ;; if there was an error, keep it - reinit! will be
             ;; required
             maybe-new-system-map-p (p/handle
                                     stopped-system-map-p
                                     (fn [_succ err]
                                       (if (some? err)
                                         (p/rejected err)
                                         (system-map/init @system-spec-a))))]

         ;; then return the result of stop! to the caller
         (p/handle
          stopped-system-map-p
          (fn [succ err]
            (if (some? err)
              (reject-fn err)
              (resolve-fn
               (system-map/dissoc-impl-keys succ)))))

         ;; update the container value
         maybe-new-system-map-p))))

  (-reinit! [_ resolve-fn reject-fn]
    (#?(:clj send :cljs swap!)
     system-map-p-container
     (fn [system-map-p]

       (let [next-system-map-p (-> system-map-p
                                   (p/handle
                                    ;; first stop the system if running
                                    (fn [succ err]
                                      (if (some? err)
                                        (p/rejected err)
                                        (system-map/stop! succ))))
                                   ;; catch any errors and ignore before reinit
                                   (p/handle
                                    (fn [_system-map err]
                                      (when (some? err)
                                        (error err "ignoring previous error"))
                                      (system-map/init @system-spec-a))))]

         ;; return result to the caller
         (p/handle
          next-system-map-p
          (fn [succ err]
            (if (some? err)
              (reject-fn err)
              (resolve-fn
               (system-map/dissoc-impl-keys succ)))))

         ;; update the container value
         next-system-map-p)))))

;; use an agent container on clj to serialize system actions
(defn slip-system
  [label system-spec]
  (map->SlipSystem
   {:label (str label)
    :system-spec-a (atom system-spec)

    :system-map-p-container
    #?(:clj (agent (p/resolved (system-map/init system-spec)))
       :cljs (atom (p/resolved (system-map/init system-spec))))}))

