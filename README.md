# slip

[clip](https://github.com/juxt/clip)'s degenerate one-trick cousin.

slip is a Clojure+Script micro-library which builds a system of objects.
It transforms a system specification into a data-driven [interceptor-chain](https://github.com/yapsterapp/a-frame/blob/trunk/src/a_frame/interceptor_chain.cljc)
and runs that interceptor-chain to create a system map.

## system specification

A system map will have keyword keys and is built according to a
`SystemSpec`, which is governed by a
[Malli schema](https://cljdoc.org/d/yapsterapp/slip/CURRENT/api/slip.schema).

A `SystemSpec` is a collection of `ObjectSpec`s, and each `ObjectSpec` must
povide:

- `:slip/key` - `<object-key>` in the system map
- `:slip/factory` - optional `<factory-key>` - defaults to `<object-key>`
- `:slip/data` - lifecycle `DataSpec` - a template specifying how to construct a
   data parameter for the factory method. It supports keyword-maps, vectors,
   references to other system objects and literal values.

A `SystemSpec` can be given in map for, with implicit `:slip/key`s:

``` clojure
{:foo {:slip/data #slip.system/ref [:config :foo]}
 :bar {:slip/factory :barfac
       :slip/data {:f #slip.system/ref :foo
                   :cfg #slip.system/ref [:config :bar]}}}
```

or, equivalently, a system can be specified in a vector form with explicit
`:slip/key`s:

``` clojure
[{:slip/key :foo :slip/data #slip.system/ref [:config :foo]}
 {:slip/key :bar
  :slip/factory :barfac
        :slip/data {:f #slip.system/ref :foo
                    :cfg #slip.system/ref [:config :bar]}}]
```

### system refs

An object from the system map can be passed to another object's lifecycle 
method using a `#slip.system/ref`. A `#slip.system/ref` will lookup 
an object from the sytem map when `:slip/data` template is expanded 
for an `ObjectSpec`. If the ref points to a `nil` location in the 
system map then an error will be thrown. If `nil` is a valid value 
for the ref then use a `#slip.system/ref?` instead.

## lifecycle methods

Objects in a system map are created and destroyed by the factory lifecycle
methods. There are two lifecycle methods and method dispatch is
on an `ObjectSpec`s `<factory-key>`.
For a given `<factory-key>` a `start` method is
required, but a `stop `method is optional (e.g. when no resource cleanup is
required).

The lifecycle method signatures are:
``` clojure
(defmulti start (fn [<factory-key> <data>]))
(defmulti stop (fn [<factory-key> <data> <object>]))
```

`<data>` is provided to lifecycle methods according to the `:slip/data`
`DataSpec` from the `ObjectSpec` - the `DataSpec` template will be expanded
with any `#slip.system/ref` references replaced with their value from the
under-construction system map. Slip identifies object dependencies and will
and start/stop objects in such an order such that all their dependencies can
be met.

## Example

``` clojure
(require '[slip.multimethods :as mm])
(require '[slip.core :as slip])

(def sys
 {:foo {:slip/data #slip.system/ref [:config :foo]}
  :bar {:slip/factory :barfac
        :slip/data {:f #slip.system/ref :foo
                    :cfg #slip.system/ref [:config :bar]}}})

(defmethod mm/start :foo
  [k d]
  d)

(defmethod mm/start :barfac
  [k {f :f
      cfg :cfg
      :as d}]
  {:foo f :bar-cfg cfg})

(def app @(slip/start sys {:config {:foo 100 :bar 200}}))

app ;; => {:config {:foo 100, :bar 200},
           :foo 100,
           :bar {:foo 100, :bar-cfg 200}}

```
