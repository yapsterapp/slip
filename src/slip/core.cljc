(ns slip.core
  (:require
   [promesa.core :as p]
   [a-frame.interceptor-chain :as ic]
   [slip.system :as system]))

(defn start
  "start a system

   - `sys-spec` - the [[slip.system.schema/SystemSpec]]
   - `init` - map of initial data for the system - can be referenced
             from the `sys-spec`
   - `debug?` - add `:slip/log` key to the returned system with details
      of the interceptors called and the data passed to them"
  ([sys-spec]
   (start sys-spec {} nil))
  ([sys-spec init]
   (start sys-spec init nil))
  ([sys-spec init {:keys [debug?]}]
   (p/let [sorted-sys-spec (system/topo-sort-system sys-spec)
           start-intc (system/start-interceptor-chain sorted-sys-spec)
           stop-intc (system/stop-interceptor-chain sorted-sys-spec)

           ;; add the start and stop chains to the system init
           ;; for later reference
           init (-> (or init {})
                    (assoc ::start start-intc)
                    (assoc ::stop stop-intc))

           {history ::ic/history
            :as outc} (ic/execute*
                       (assoc start-intc :slip/system init))]

     (cond-> (get-in outc [:slip/system])
       debug? (assoc :slip/log history)))))

(defn stop
  "stop a system
  - `sys` - a system started with [[start]]"
  [{stop-intc ::stop
    :as sys}]

  (when (nil? stop-intc)
    (throw
     (ex-info "no ::stop chain in system" {:system sys})))

  (p/let [outc (ic/execute*
                (assoc stop-intc :slip/system sys))]

    (get-in outc [:slip/system])))
