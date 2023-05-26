(ns slip.reload
  "some slightly awkward clj-only macros to def a system
   and associated fns including a c.t.n.r/refresh-based
   reload! operation"
  (:require
   [clojure.string :as string]
   [clojure.tools.namespace.repl :refer [refresh]]
   [promesa.core :as p]
   [slip.system :as system]))

(defn ^:private join-name
  [& ss]
  (->> ss
       (filter some?)
       (string/join "-")))

(defmacro defsys-fns
  ([system-spec] `(defsys-fns nil ~system-spec))
  ([nm system-spec]
   (let [base-name (some-> nm name)
         sys-sym (symbol (join-name "sys" base-name))
         sys-start-sym (symbol (str (join-name "start" base-name) "!"))
         reload-after-sym (symbol (str *ns*) (str (join-name "start" base-name) "!"))
         sys-stop-sym (symbol (str (join-name "stop" base-name) "!"))
         sys-reinit-sym (symbol (str (join-name "reinit" base-name) "!"))
         sys-reload-sym (symbol (str (join-name "reload" base-name) "!"))
         sys-label (str *ns* "/" (name sys-sym))]
     `(do
        (defonce ~sys-sym
          (system/slip-system ~sys-label ~system-spec))

        (defn ~sys-start-sym
          []
          (system/start! ~sys-sym))

        (defn ~sys-stop-sym
          []
          (system/stop! ~sys-sym))

        (defn ~sys-reinit-sym
          []
          (system/reinit! ~sys-sym))

        (defn ~sys-reload-sym
          []
          (p/chain
           (system/stop! ~sys-sym)
           (fn [_#]
             (refresh :after (quote ~reload-after-sym)))))))))
