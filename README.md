# slip

[![Build Status](https://github.com/yapsterapp/slip/actions/workflows/clojure.yml/badge.svg)](https://github.com/yapsterapp/slip/actions)
[![Clojars Project](https://img.shields.io/clojars/v/com.github.yapsterapp/slip.svg)](https://clojars.org/com.github.yapsterapp/slip)
[![cljdoc badge](https://cljdoc.org/badge/com.github.yapsterapp/slip)](https://cljdoc.org/d/com.github.yapsterapp/slip)

A bit like [clip](https://github.com/juxt/clip)'s degenerate one-trick cousin.

slip is a Clojure+Script micro-library which builds a system of objects.
It transforms a pure-data system specification into a pure-data [interceptor-chain](https://github.com/yapsterapp/a-frame/blob/trunk/src/a_frame/interceptor_chain.cljc)
description and then runs that interceptor-chain to create a system map.
Errors during
construction of the system map cause the operation to be unwound gracefully,
avoiding leaving objects in unknown state.

## system specification

A system map will have keyword keys and is built according to a
`SystemSpec`, which is governed by a
[Malli schema](https://cljdoc.org/d/yapsterapp/slip/CURRENT/api/slip.schema).

A `SystemSpec` is a collection of `ObjectSpec`s. An `ObjectSpec` describes
how to create and destroy an object in a system map. Each `ObjectSpec` provides:

- `:slip/key` - the `<object-key>` - a keyword key for the object in the
   system map
- `:slip/factory` - optional `<factory-key>` keyword - identifies a lifecycle
    method for creating and destroying the object. Defaults to `<object-key>`
- `:slip/data` - lifecycle method `DataSpec` - a template specifying how
   to build the data parameter for a lifecycle method.
   It supports keyword-maps, vectors,
   references to other system objects and literal values.

A `SystemSpec` can be given in map form, with implicit `:slip/key`s:

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

## lifecycle methods

Objects in a system map are created and destroyed by the factory lifecycle
methods. There are two lifecycle methods, `start` and `stop`, and method
dispatch is on an `ObjectSpec`s `<factory-key>`
(which defaults to the `<object-key>`).

For a given `<factory-key>`, a `start` method is
required, but a `stop `method is optional (so need not be provided when no
resource cleanup is necessary).

The lifecycle method signatures are:
``` clojure
(defmulti start (fn [<factory-key> <data>]))
(defmulti stop (fn [<factory-key> <data> <object>]))
```

### lifecycle method data and system refs

The `<data>` parameter for lifecycle methods is built according
to the `:slip/data` `DataSpec` template from an `ObjectSpec`.

`DataSpec` templates are expanded with any `#slip.system/ref` references
replaced with the value referred to from
the (under-construction) system map - thus a DAG of objects can 
be created. 

Slip identifies object dependencies and
will `start`/`stop` objects in such an order such that all their dependencies
can be met.

If a reference points to a `nil` location in the
system map then an error will be thrown. If `nil` is a valid value
for the ref then using a `#slip.system/ref?` will not cause an error.

## Example

``` clojure
(require '[promesa.core :as p])
(require '[slip.multimethods :as mm])
(require '[slip.core :as slip])

(def sys
 {:foo {:slip/data #slip.system/ref [:config :foo]}
  :bar {:slip/factory :barfac
        :slip/data {:f #slip.system/ref :foo
                    :cfg #slip.system/ref [:config :bar]}}})

(defmethod mm/start :foo
  [k d]
  (p/delay 100 d))

(defmethod mm/start :barfac
  [k {f :f
      cfg :cfg
      :as d}]
  (p/delay
    100
    {:foo f
     :bar-cfg cfg}))

(def app @(slip/start sys {:config {:foo 100 :bar 200}}))

app ;; => {:config {:foo 100, :bar 200},
    ;;     :foo 100,
    ;;     :bar {:foo 100, :bar-cfg 200}}

```

## ClojureScript

It's common for JavaScript object factories to return a Promise of their result.
Slip is fully Promise compatible - the interceptor chain is promise-based and
any of the lifecycle fns can return a promise of their result, as the above
example demonstrates.

## debugging

You can see exactly what happened during construction of your system by 
providing a`:slip/debug?` option to `start`

``` clojure
(def app @(slip/start
           sys
           {:config {:foo 100 :bar 200}}
           {:slip/debug? true}))
```

your system will get a `:slip/log` key with a detailed breakdown of the
interceptor fns called, with what data and what outcome. Here's the log for 
the example above - each log entry has 
[`ObjectSpec` `<interceptor-fn>` `<interceptor-action>` `<data>` `<outcome>`]

``` clojure
[[#:a-frame.interceptor-chain{:key :slip.system/start,
                              :enter-data
                              #:slip{:data #slip.system/ref [:config :foo],
                                     :key :foo}}
  :a-frame.interceptor-chain/enter
  :a-frame.interceptor-chain/execute
  #:slip{:data 100, :key :foo}
  :a-frame.interceptor-chain/success]
  
 [#:a-frame.interceptor-chain{:key :slip.system/start,
                              :enter-data
                              #:slip{:factory :barfac,
                                     :data
                                     {:f #slip.system/ref [:foo],
                                      :cfg #slip.system/ref [:config :bar]},
                                     :key :bar}}
  :a-frame.interceptor-chain/enter
  :a-frame.interceptor-chain/execute
  #:slip{:factory :barfac, :data {:f 100, :cfg 200}, :key :bar}
  :a-frame.interceptor-chain/success]
  
 [#:a-frame.interceptor-chain{:key :slip.system/start,
                              :enter-data
                              #:slip{:factory :barfac,
                                     :data
                                     {:f #slip.system/ref [:foo],
                                      :cfg #slip.system/ref [:config :bar]},
                                     :key :bar}}
  :a-frame.interceptor-chain/leave
  :a-frame.interceptor-chain/noop
  :_
  :a-frame.interceptor-chain/success]
  
 [#:a-frame.interceptor-chain{:key :slip.system/start,
                              :enter-data
                              #:slip{:data #slip.system/ref [:config :foo],
                                     :key :foo}}
  :a-frame.interceptor-chain/leave
  :a-frame.interceptor-chain/noop
  :_
  :a-frame.interceptor-chain/success]]
```

