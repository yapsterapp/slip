(ns slip.system-test
  (:require
   [promesa.core :as p]
   [promisespromises.test :refer [deftest is testing]]
   [a-frame.interceptor-chain :as intc]
   [slip.multimethods :as slip.mm]
   [slip.system :as sut]))

(defmethod slip.mm/start :foofoo
  [_ _data]
  ::foofoo)

(defmethod slip.mm/start :bar
  [_ data]
  (assoc data :bar ::bar))

(deftest simple-system
  (p/let [sys [{:slip/key :foo, :slip/factory :foofoo, :slip/data {}}
               {:slip/key :bar, :slip/data {:f #slip.system/ref [:foo]}}]

          start-intc (sut/start-interceptor-chain
                      (sut/topo-sort-system sys))

          out (intc/execute* start-intc)]

    (is (nil? out))
    )
  )
