(ns slip.reloaded
  "reloaded workflow support

   some slightly awkward clj-only macros to def a system
   and associated fns including a c.t.n.r/refresh-based
   reload! operation"
  {:cljdoc/languages ["clj"]}
  (:require
   [clojure.string :as string]
   [clojure.tools.namespace.repl :refer [refresh]]
   [promesa.core :as p]
   [slip.core :as core]))

(defn ^:private join-name
  [& ss]
  (->> ss
       (filter some?)
       (string/join "-")))

(defmacro defsys-fns
  "defs a system `sys` and `start!`, `stop!`, `reinit!` and `reload!` fns

   - `nm` - def `sys-<nm>`, `start-<nm>!` &c instead"
  ([system-spec] `(defsys-fns nil ~system-spec))
  ([nm system-spec]
   (let [base-name (some-> nm name)
         sys-sym (symbol (join-name "sys" base-name))
         spec-sym (symbol (join-name "spec" base-name))
         reset-spec-sym (symbol (str (join-name "reset-spec" base-name) "!"))
         system-map-sym (symbol (join-name "system-map" base-name))
         sys-start-sym (symbol (str (join-name "start" base-name) "!"))
         reload-after-sym (symbol (str *ns*) (str (join-name "start" base-name) "!"))
         sys-stop-sym (symbol (str (join-name "stop" base-name) "!"))
         sys-reinit-sym (symbol (str (join-name "reinit" base-name) "!"))
         sys-reload-sym (symbol (str (join-name "reload" base-name) "!"))]
     `(do
        (core/defsys ~sys-sym ~system-spec)

        (defn ~spec-sym
          []
          (core/spec ~sys-sym))

        (defn ~reset-spec-sym
          [system-spec#]
          (core/reset-spec! ~sys-sym system-spec#))

        (defn ~system-map-sym
          []
          (core/system-map ~sys-sym))

        (defn ~sys-start-sym
          ([]
           (core/start! ~sys-sym))
          ([opts#]
           (core/start! ~sys-sym opts#)))

        (defn ~sys-stop-sym
          ([]
           (core/stop! ~sys-sym))
          ([opts#]
           (core/stop! ~sys-sym opts#)))

        (defn ~sys-reinit-sym
          []
          (core/reinit! ~sys-sym))

        (defn ~sys-reload-sym
          []
          ;; yes. we're really derefing the promise. if we chain
          ;; c.t.n.r/refresh borks because of an `in-ns` on
          ;; a promesa thread
          (let [_# @(p/let [_# (p/handle
                                (core/stop! ~sys-sym)
                                (fn [succ# err#]
                                  (if (some? err#)
                                    [:error err#]
                                    [:success succ#])))]

                      ;; reinit! in case of errors
                      (core/reinit! ~sys-sym))]

            (refresh :after (quote ~reload-after-sym))))))))
