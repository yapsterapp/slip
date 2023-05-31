(ns slip.system
  "a container for a stateful system of objects,
   and the specification for the system.

   both the system and the specification can be updated.
   the system is lazily created, but is safe for access and
   update in multi-threaded scenarios"
  (:require
   [promesa.core :as p]
   [slip.protocols :as pt]
   [slip.system-map :as system-map]))

(defrecord SlipSystem [label system-spec-a system-map-p-container]
  pt/ISlipSystem
  (-spec [_] @system-spec-a)

  (-reset-spec! [_ system-spec]
    (reset! system-spec-a system-spec))

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
              (resolve-fn succ))))

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
             ;; reinitialise it with potentially updated spec
             maybe-new-system-map-p (p/handle
                                     stopped-system-map-p
                                     (fn [_succ err]
                                       (if (some? err)
                                         (p/rejected err)
                                         (system-map/init @system-spec-a))))]

         ;; return a result to the caller
         (p/handle
          stopped-system-map-p
          (fn [succ err]
            (if (some? err)
              (reject-fn err)
              (resolve-fn succ))))

         ;; update the container value
         maybe-new-system-map-p))))

  (-reinit! [_ resolve-fn reject-fn]
    (#?(:clj send :cljs swap!)
     system-map-p-container
     (fn [system-map-p]

       (let [next-system-map-p (p/handle
                                system-map-p
                                (fn [_system-map _err]
                                  (system-map/init @system-spec-a)))]

         ;; return result to the caller
         (p/handle
          next-system-map-p
          (fn [succ err]
            (if (some? err)
              (reject-fn err)
              (resolve-fn succ))))

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

(defn spec
  [sys]
  (pt/-spec sys))

(defn reset-spec!
  [sys system-spec]
  (pt/-reset-spec! sys system-spec))

(defn start!
  "start a system. idempotent - does nothing if the system is already started"
  ([sys] (start! sys nil))
  ([sys opts]
   (p/create
    (fn [resolve-fn reject-fn]
      (pt/-start! sys opts resolve-fn reject-fn)))))

(defn stop!
  "stop a system. idempotent - does nothing if the system is already stopped"
  ([sys] (stop! sys nil))
  ([sys opts]
   (p/create
    (fn [resolve-fn reject-fn]
      (pt/-stop! sys opts resolve-fn reject-fn)))))

(defn reinit!
  "re-initialise an errored system. does nothing if the system is not errored"
  [sys]
  (p/create
   (fn [resolve-fn reject-fn]
     (pt/-reinit! sys resolve-fn reject-fn))))
