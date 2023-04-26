(ns slip.schema
  (:require
   [malli.util :as mu]
   [slip.data.ref-path :as ref-path]))

(def DataSpec
  "a recursive schema specifying a template for building data parameters
   for factory methods, allowing references to already created objects
   with `#slip/ref` tagged literals

   - `::ref-path-spec` - a `RefPath` e.g. from a #slip/ref tagged literal
   - `::map-data-spec` - a `{<keyword> <DataSpec>}` map
   - `::vector-data-spec` - a `[<DataSpec>]`
   - `<anything-else>` - a literal value"

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
  "an ObjectSpec with no key - to be used where keys
   are implicit, in a [[MapSystemSpec]]"
  [:map
   [:slip/factory {:optional true} :keyword]
   [:slip/data DataSpec]])

(def KeyedObjectSpec
  "an ObjectSpec with a key - to be used where keys must
   be explicit, in a [[VectorSystemSpec]]"
  (mu/merge
   KeylessObjectSpec
   [:map
    [:slip/key :keyword]]))

(def VectorSystemSpec
  "a [[SystemSpec]] with explicit order"
  [:vector KeyedObjectSpec])

(def MapSystemSpec
  "a [[SystemSpec]] with no explicit order"
  [:map-of :keyword KeylessObjectSpec])

(def SystemSpec
  "a SystemSpec which may be presented as a map of
   [[KeylessObjectSpec]] or a vector of [[KeyedObjectSpec]]"
  [:or VectorSystemSpec MapSystemSpec])
