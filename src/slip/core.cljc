(ns slip.core
  (:require
   [promesa.core :as p]
   [slip.protocols :as pt]
   [slip.system :as system]))

(defmacro defsys
  "defonce a system at the given var"
  [nm system-spec]
  (let [sys-name (str *ns* "/" (name nm))]
    `(do
       (defonce ~nm (system/slip-system ~sys-name ~system-spec))

       ;; update the spec in an existing defonce
       (pt/-reset-spec! ~nm ~system-spec))))

(defn spec
  "return the current system-spec. this may not reflect the
   running system, if the system-spec has been reset"
  [sys]
  (pt/-spec sys))

(defn reset-spec!
  "reset the system-spec - this will not take effect until the
   system is restarted"
  [sys system-spec]
  (pt/-reset-spec! sys system-spec))

(defn system-map
  "return the full active system-map, including the implementation
   keys"
  [sys]
  (pt/-system-map sys))

(defn start!
  "start a system. idempotent - does nothing if the system is already started

   - returns the system-map, minus any implementation keys"
  ([sys] (start! sys nil))
  ([sys opts]
   (p/create
    (fn [resolve-fn reject-fn]
      (pt/-start! sys opts resolve-fn reject-fn)))))

(defn stop!
  "stop a system. idempotent - does nothing if the system is already stopped

   - returns the system-map, minus any implementation keys (which will be `{}`
     after a successful `stop!`)"
  ([sys] (stop! sys nil))
  ([sys opts]
   (p/create
    (fn [resolve-fn reject-fn]
      (pt/-stop! sys opts resolve-fn reject-fn)))))

(defn reinit!
  "re-initialise a system. Errors are cleared and any pending system-spec
   is used to initialise a new system

   - returns the system-map, minus implementation keys (which will be `{}`)"
  [sys]
  (p/create
   (fn [resolve-fn reject-fn]
     (pt/-reinit! sys resolve-fn reject-fn))))
