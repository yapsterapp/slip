(ns slip.schema
  (:require
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

(def DefaultKeyObjectSpec
  [:tuple :keyword DataSpec])

(def ExplicitKeyObjectSpec
  [:tuple :keyword :keyword DataSpec])

(def MapObjectSpec
  [:map
   [:key :keyword]
   [:factory {:optional true} :keyword]
   [:data DataSpec]])

(def SystemObjectSpec
  [:or MapObjectSpec DefaultKeyObjectSpec ExplicitKeyObjectSpec])

(def SystemSpec
  [:vector SystemObjectSpec])
