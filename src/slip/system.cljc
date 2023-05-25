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
         stopped-system-map-p)))))

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
  ([sys] (start! sys nil))
  ([sys opts]
   (p/create
    (fn [resolve-fn reject-fn]
      (pt/-start! sys opts resolve-fn reject-fn)))))

(defn stop!
  ([sys] (stop! sys nil))
  ([sys opts]
   (p/create
    (fn [resolve-fn reject-fn]
      (pt/-stop! sys opts resolve-fn reject-fn)))))
