(ns slip.core
  (:require
   [slip.system :as system]))

(defmacro defsys
  "def a system at the given var"
  [nm system-spec]
  (let [sys-name (str *ns* "/" (name nm))]
    `(def ~nm (system/slip-system ~sys-name ~system-spec))))

(defn start!
  "start a system"
  ([sys] (system/start! sys))
  ([sys opts] (system/start! sys opts)))

(defn stop!
  "stop a system"
  ([sys] (system/stop! sys))
  ([sys opts] (system/stop! sys opts)))

(defn reinit!
  "re-initialise an errored system. does nothing if the system
   is not errored"
  [sys] (system/reinit! sys))
