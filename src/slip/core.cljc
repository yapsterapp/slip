(ns slip.core
  (:require
   [malli.experimental :as mx]
   [slip.kahn :as kahn]
   [slip.data.ref-path :as ref-path]
   [slip.data.resolve :as res]
   [slip.schema :as s]))

(defn normalise-obj-spec
  [obj-spec]
  (if (= 2 (count obj-spec))
    [(first obj-spec) (first obj-spec) (second obj-spec)]
    obj-spec))

(defn top-level-refs
  [dspec]
  (let [refs (res/collect-refs dspec)]
    (->> refs
         (map ref-path/path)
         (map first)
         (into #{}))))

(mx/defn topo-sort-system :- s/SystemSpec
  "given a system-spec returns the system-spec
   topo-sorted by ref dependencies"
  [sys :- s/SystemSpec]

  (let [sys-map (into
                 {}
                 (for [obj-spec sys]
                   (let [[k :as nos] (normalise-obj-spec obj-spec)]
                     [k nos])))

        k-deps (into
                {}
                (for [obj-spec sys]
                  (let [[k _fk dspec] (normalise-obj-spec obj-spec)]
                    [k (top-level-refs dspec)])))

        _ (prn k-deps)

        sorted-keys (kahn/kahn-sort k-deps)]

    (into
     []
     (for [k (reverse sorted-keys)]
       [k (get sys-map k)]))))

(mx/defn interceptor-chain
  [sys :- s/SystemSpec])
