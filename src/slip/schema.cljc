(ns slip.schema
  (:require
   [malli.util :as mu]
   [slip.data.ref-path :as ref-path]))

(def DataSpec
  [:schema
   {:registry
    {::data-spec [:or
                  [:ref ::ref-path-spec]
                  [:ref ::map-data-spec]
                  [:ref ::vector-data-spec]
                  [:not [:or map? vector? [:ref ::ref-path-spec]]]]

     ::ref-path-spec [:fn ref-path/ref-path?]
     ::map-data-spec [:map-of :keyword [:ref ::data-spec]]
     ::vector-data-spec [:vector [:ref ::data-spec]]}}

   ::data-spec])

(def KeylessObjectSpec
  [:map
   [:slip/factory {:optional true} :keyword]
   [:slip/data DataSpec]])

(def KeyedObjectSpec
  (mu/merge
   KeylessObjectSpec
   [:map
    [:slip/key :keyword]]))

(def VectorSystemSpec
  [:vector KeyedObjectSpec])

(def MapSystemSpec
  [:map-of :keyword KeylessObjectSpec])

(def SystemSpec
  [:or VectorSystemSpec MapSystemSpec])
