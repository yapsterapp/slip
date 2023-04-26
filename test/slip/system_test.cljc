(ns slip.system-test
  (:require
   [promesa.core :as p]
   [promisespromises.test :refer [deftest is testing]]
   [a-frame.interceptor-chain :as intc]
   [slip.multimethods :as slip.mm]
   [slip.system :as sut]))

(defmethod slip.mm/start :foofoo
  [k d]
  ::foofoo)

(defmethod slip.mm/stop :foofoo
  [k d o])

(defmethod slip.mm/start :bar
  [k d]
  (assoc d :bar ::bar))

(defmethod slip.mm/stop :bar
  [k d o]
  )

(defmethod slip.mm/start :baz
  [k {b :b :as d}]
  ;; (throw (ex-info "boo" {}))
  {:b b
   :baz 100}
  )

(defmethod slip.mm/stop :baz
  [k d o]
  )

(deftest simple-system
  (p/let [sys-spec [{:slip/key :foo, :slip/factory :foofoo, :slip/data {}}
                    {:slip/key :bar, :slip/data {:f #slip/ref [:foo]}}
                    {:slip/key :baz  :slip/data {:b #slip/ref :bar}}]

          start-intc (sut/start-interceptor-chain
                      (sut/topo-sort-system sys-spec))

          out-ctx (intc/execute* start-intc)

          sys (get-in out-ctx [:slip/system])]

    (is (= {:foo ::foofoo
            :bar {:f ::foofoo :bar ::bar}
            :baz {:b {:f ::foofoo :bar ::bar} :baz 100}}
           sys))))
