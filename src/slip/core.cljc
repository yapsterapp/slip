(ns slip.core
  (:require
   [promesa.core :as p]
   [a-frame.interceptor-chain :as ic]
   [slip.system :as system]))

(defn init
  "initialise a system - given a sys-spec return an initialised
   but not started system map, with `:slip/start` and `:slip/stop` keys
   for the interceptor chain descriptions"
  [sys-spec]
  (let [sorted-sys-spec (system/topo-sort-system sys-spec)

        start-intc (system/start-interceptor-chain sorted-sys-spec)
        stop-intc (system/stop-interceptor-chain sorted-sys-spec)]

    (-> {}
        (assoc :slip/start start-intc)
        (assoc :slip/stop stop-intc))))

(defn start!
  "start a system
   - `sys` - an `init`ialised system
   - `:slip/debug?` - add a `:slip/log` key to the returned system
      with details of the interceptor fns called

   returns the started system"
  ([sys] (start! sys nil))
  ([{start-intc :slip/start
     stop-intc :slip/stop
     :as sys}
    {:keys [:slip/debug?]}]

   (when (or (nil? start-intc)
             (nil? stop-intc))
     (throw
      (ex-info "system must have :slip/start and :slip/stop chains"
               {:system sys})))

   (if (= 2 (count (keys sys)))
     (p/let [start-intc+sys (assoc start-intc :slip/system {})
             {history ::ic/history
              :as r} (ic/execute* start-intc+sys)

             sys (get-in r [:slip/system])]

       ;; add the start and stop chains and an optional
       ;; debug log to the system
       (cond-> sys
         true (assoc :slip/start start-intc)
         true (assoc :slip/stop stop-intc)
         debug? (assoc :slip/log history)))

     (p/resolved sys))))

(defn stop!
  "stop a system
   - `sys` - a `start`ed system
   - `:slip/debug?` - add a `:slip/log` key to the returned system with
     details of the interceptor fns called

   returns the stopped system - unless errors happened, it should be
     equivalent to a freshly `init`ed system"
  ([sys] (stop! sys nil))
  ([{start-intc :slip/start
     stop-intc :slip/stop
     _running-system :slip/system
     :as sys}
    {:keys [:slip/debug?]}]

   (when (or (nil? start-intc)
             (nil? stop-intc))
     (throw
      (ex-info "system must have :slip/start and :slip/stop chains"
               {:system sys})))

   (if (> (count (keys sys)) 2)
     (p/let [stop-intc+sys (assoc stop-intc
                                  :slip/system
                                  (dissoc sys :slip/start :slip/stop :slip/log))

             {history ::ic/history
              :as r} (ic/execute* stop-intc+sys)

             out-sys (get-in r [:slip/system])]

       (cond-> out-sys
         true (assoc :slip/start start-intc)
         true (assoc :slip/stop stop-intc)
         debug? (assoc :slip/log history)))

     (p/resolved sys))))
