(ns slip.system
  (:require
   [promesa.core :as p]
   [slip.protocols :as pt]
   [slip.system-map :as system-map]))

(defrecord SlipSystem [label system-spec system-map-p-container]
  pt/ISlipSystem
  (-spec [_] system-spec)

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

       ;; if we are in an errored state, recover with
       ;; a fresh system
       (let [stopped-system-map-p (p/then
                                   system-map-p
                                   #(system-map/stop! % opts))]

         ;; return a result to the caller
         (p/handle
          stopped-system-map-p
          (fn [succ err]
            (if (some? err)
              (reject-fn err)
              (resolve-fn succ))))

         ;; update the container value
         stopped-system-map-p))))

  (-reinit! [_ resolve-fn reject-fn]
    (#?(:clj send :cljs swap!)
     system-map-p-container
     (fn [system-map-p]

       (let [next-system-map-p (p/handle
                                system-map-p
                                (fn [system-map err]
                                  (if (some? err)
                                    (system-map/init system-spec)
                                    system-map)))]

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
    :system-spec system-spec

    :system-map-p-container
    #?(:clj (agent (p/resolved (system-map/init system-spec)))
       :cljs (atom (p/resolved (system-map/init system-spec))))}))

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
