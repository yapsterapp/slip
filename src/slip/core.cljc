(ns slip.core
  (:require
   [promesa.core :as p]
   [a-frame.interceptor-chain :as ic]
   [slip.system :as system]))

(defn start
  "start a system

   - `sys-spec` - the [[slip.schema/SystemSpec]]
   - `init` - map of initial data for the system - can be referenced
             from the `sys-spec`
   - `:slip/debug?` - add a `:slip/log` key to the returned system with details
      of the interceptor fns called and the data passed to them"
  ([sys-spec]
   (start sys-spec {} nil))
  ([sys-spec init]
   (start sys-spec init nil))
  ([sys-spec init {:keys [:slip/debug?]}]
   (p/let [sorted-sys-spec (system/topo-sort-system sys-spec)
           start-intc (system/start-interceptor-chain sorted-sys-spec)
           stop-intc (system/stop-interceptor-chain sorted-sys-spec)

           init (-> (or init {}))

           start-intc (assoc start-intc :slip/system init)

           {history ::ic/history
            :as r} (ic/execute* start-intc)

           sys (get-in r [:slip/system])]

     ;; add the start and stop chains and an optional
     ;; debug log to the system for later reference
     (cond-> sys
       true (assoc ::start start-intc)
       true (assoc ::stop stop-intc)
       debug? (assoc :slip/log history)))))

(defn stop
  "stop a system
  - `sys` - a system started with [[start]]"
  ([sys] (stop sys {}))
  ([{start-intc ::start
     stop-intc ::stop
     :as sys}
    {:keys [:slip/debug?]}]

   (when (nil? stop-intc)
     (throw
      (ex-info "no ::stop chain in system" {:system sys})))

   (p/let [stop-intc+sys (assoc stop-intc
                                :slip/system
                                (dissoc sys ::start ::stop :slip/log))

           {history ::ic/history
            :as r} (ic/execute* stop-intc+sys)

           out-sys (get-in r [:slip/system])]

     (cond-> out-sys
       true (assoc ::start start-intc)
       true (assoc ::stop stop-intc)
       debug? (assoc :slip/log history)))))
