# slip

[![Build Status](https://github.com/yapsterapp/slip/actions/workflows/clojure.yml/badge.svg)](https://github.com/yapsterapp/slip/actions)
[![Clojars Project](https://img.shields.io/clojars/v/com.github.yapsterapp/slip.svg)](https://clojars.org/com.github.yapsterapp/slip)
[![cljdoc badge](https://cljdoc.org/badge/com.github.yapsterapp/slip)](https://cljdoc.org/d/com.github.yapsterapp/slip)

A bit like [clip](https://github.com/juxt/clip)'s degenerate one-trick cousin.

slip is a Clojure+Script IOC micro-library which builds a system of objects.
It transforms a pure-data system specification into a pure-data [interceptor-chain](https://github.com/yapsterapp/a-frame/blob/trunk/src/a_frame/interceptor_chain.cljc)
description and then runs an asynchronous interceptor-chain to create a system
map.
Errors during construction of the system map cause the operation to be
unwound gracefully, avoiding leaving any objects in unknown states.

## why?

There are a few IOC libs around for Clojure and ClojureScript -
[Component](https://github.com/stuartsierra/component),
[Mount](https://github.com/tolitius/mount),
[Integrant](https://github.com/weavejester/integrant) and
[Clip](https://github.com/juxt/clip). See Clip's README for a detailed
comparison of the features of these libs.

Only Clip attempts to deal with asynchronous
(i.e. promise returning) factory functions.
Slip takes a similar approach to Clip and deals well with asynchronous
factories, but it uses a different extension mechanism - avoiding
code-as-data difficulties on ClojureScript, but making other trade-offs
in the process.

## The system specification

Slip builds system maps. A system map will have keyword keys and is
built according to a system specification - a `SystemSpec`, which is governed
by a
[Malli schema](https://github.com/yapsterapp/slip/blob/trunk/src/slip/schema.cljc).

A `SystemSpec` is a collection of `ObjectSpec`s. An `ObjectSpec` describes
how to create and destroy an individual object in a system map.
Each `ObjectSpec` provides:

- `:slip/key` - the `<object-key>` - a keyword key for the object in the
   system map
- `:slip/factory` - optional `<factory-key>` keyword - identifies a lifecycle
    method for creating and destroying the object. Defaults to `<object-key>`
- `:slip/data` - a `DataSpec` template for data provided to lifecycle methods
   - the templates supports keyword-maps, vectors,
   references to other system objects and literal values.

A `SystemSpec` can be given in map form, with implicit `:slip/key`s:

``` clojure
{:foo {:slip/data #slip/ref [:config :foo]}
 :bar {:slip/factory :barfac
       :slip/data {:f #slip/ref :foo
                   :cfg #slip/ref [:config :bar]}}}
```

or, equivalently, a system can be specified in a vector form with explicit
`:slip/key`s:

``` clojure
[{:slip/key :foo :slip/data #slip/ref [:config :foo]}
 {:slip/key :bar
  :slip/factory :barfac
  :slip/data {:f #slip/ref :foo
              :cfg #slip/ref [:config :bar]}}]
```

## lifecycle methods

Objects in a system map are created and destroyed by the factory lifecycle
methods. There are two lifecycle methods, `start` and `stop`, and method
dispatch is on an `ObjectSpec`s `<factory-key>`
(which defaults to the `<object-key>`). The lifecycle method implementations
should either return constructed objects directly, or return a promise of
the constructed object.

For a given `<factory-key>`, a `start` method is
required, but a `stop `method is optional (so need not be provided when no
resource cleanup is necessary).

The lifecycle method signatures are:

``` clojure
(defmulti start (fn [<factory-key> <data>]))
(defmulti stop (fn [<factory-key> <data> <object>]))
```

and an application should provide implementations of these methods for each type
of object to be managed.

### lifecycle method data and system refs

The `<data>` parameter for lifecycle methods is built according
to the `:slip/data`
[`DataSpec`](https://github.com/yapsterapp/slip/blob/trunk/src/slip/schema.cljc)
template from an `ObjectSpec`.

`DataSpec` templates are expanded with any `#slip/ref` references
replaced with the value referred to from
the (under-construction) system map - thus a DAG of objects can
be created.

Slip identifies object dependencies and
will `start`/`stop` objects in such an order that all dependencies
can be met.

If a reference points to a `nil` location in the
system map then an error will be thrown. If `nil` is a valid value
for the reference then using a `#slip/ref?` will not cause an error.

## Example

``` clojure
(require '[promesa.core :as p])
(require '[slip.multimethods :as mm])
(require '[slip.core :as slip])

(def sys-spec
 {:config {:slip/data {:foo 100 :bar 200}}
  :foo {:slip/data #slip/ref [:config :foo]}
  :bar {:slip/factory :barfac
        :slip/data {:f #slip/ref :foo
                    :cfg #slip/ref [:config :bar]}}})

(defmethod mm/start :config
  [k d]
  d)

(defmethod mm/start :foo
  [k d]
  (p/delay 0 d))

(defmethod mm/start :barfac
  [k {f :f
      cfg :cfg
      :as d}]
  (p/delay
    0
    {:foo f
     :bar-cfg cfg}))

(slip/defsys sys sys-spec)

(def app @(slip/start! sys))

app ;; => {:config {:foo 100, :bar 200},
    ;;     :foo 100,
    ;;     :bar {:foo 100, :bar-cfg 200}}

```

## Reloaded workflow

Slip supports a reloaded workflow with the `slip.reloaded` namespace (for
Clojure only). The `defsys-fns` macro defs a system and some 0-args
versions of `start!`, `stop!`, `reinit!` and the other `slip.core` fns -
and adds a `reload!` fn which does a `stop!`, `c.t.n.r/refresh` and
`start!` of the system.

Thus, after a bunch of code changes, a call to `@(reload!)` will load the
new code and restart the system.

``` clojure
(ns slip.sample
  (:require
    [slip.multimethods :as slip.mm]
    [slip.reloaded :refer [defsys-fns]]))

(defmethod mm/start :config
  [k d]
  d)

(defmethod mm/start :foo
  [k d]
  (p/delay 0 d))

(def sys-spec
 {:config {:slip/data {:foo 100 :bar 200}}
  :foo {:slip/data #slip/ref [:config :foo]}})

(defsys-fns sys-spec)
```

in the REPL:

``` clojure
(in-ns 'slip.sample')

@(start!)
;; => {:config {:foo 100, :bar 200}, :foo 100}

;; does a (stop!), a c.t.n.r/refresh followed by a (start!)
@(reload!)
;; => {:config {:foo 100, :bar 200}, :foo 100}

```

## ClojureScript

It's common for JavaScript object factories to return a Promise of their result.
Slip is fully Promise compatible - the interceptor chain is promise-based and
any of the lifecycle fns can return a promise of their result, as the above
examples demonstrate (`:foo` `start` fn returns a Promesa `delay`).

## debugging

You can see exactly what happened during construction of your system by
providing a`:slip/debug?` option to `start`

``` clojure
@(slip/start! sys {:slip/debug? true})
```

your system will get a `:slip/log` key with a detailed breakdown of the
interceptor fns called, with what data and what outcome. To see the log
you will need to access the full system-map ( `start!` removes implementation 
keys for clairty) - it is acessible with `@(slip/system-map sys)`.
Here's the log for the first example above - each log entry has:
[`ObjectSpec` `<interceptor-fn>` `<interceptor-action>` `<data>` `<outcome>`].
Of particular interest is the `<data>` field which shows resolved data 
for that object factory.

``` clojure
[[:a-frame.std-interceptors/unhandled-error-report
   :a-frame.interceptor-chain/enter
   :a-frame.interceptor-chain/noop
   :_
   :a-frame.interceptor-chain/success]
  [{:a-frame.interceptor-chain/key :slip.interceptors/start,
    :slip.interceptors/object #:slip{:data {:foo 100, :bar 200}, :key :config}}
   :a-frame.interceptor-chain/enter
   :a-frame.interceptor-chain/execute
   {:foo 100, :bar 200}
   :a-frame.interceptor-chain/success]
  [{:a-frame.interceptor-chain/key :slip.interceptors/start,
    :slip.interceptors/object
    #:slip{:data #slip/ref [:config :foo], :key :foo}}
   :a-frame.interceptor-chain/enter
   :a-frame.interceptor-chain/execute
   100
   :a-frame.interceptor-chain/success]
  [{:a-frame.interceptor-chain/key :slip.interceptors/start,
    :slip.interceptors/object
    #:slip{:factory :barfac,
           :data {:f #slip/ref [:foo], :cfg #slip/ref [:config :bar]},
           :key :bar}}
   :a-frame.interceptor-chain/enter
   :a-frame.interceptor-chain/execute
   {:f 100, :cfg 200}
   :a-frame.interceptor-chain/success]
  [{:a-frame.interceptor-chain/key :slip.interceptors/start,
    :slip.interceptors/object
    #:slip{:factory :barfac,
           :data {:f #slip/ref [:foo], :cfg #slip/ref [:config :bar]},
           :key :bar}}
   :a-frame.interceptor-chain/leave
   :a-frame.interceptor-chain/noop
   :_
   :a-frame.interceptor-chain/success]
  [{:a-frame.interceptor-chain/key :slip.interceptors/start,
    :slip.interceptors/object
    #:slip{;; => :data #slip/ref [:config :foo], :key :foo}}
   :a-frame.interceptor-chain/leave
   :a-frame.interceptor-chain/noop
   :_
   :a-frame.interceptor-chain/success]
  [{:a-frame.interceptor-chain/key :slip.interceptors/start,
    :slip.interceptors/object #:slip{:data {:foo 100, :bar 200}, :key :config}}
   :a-frame.interceptor-chain/leave
   :a-frame.interceptor-chain/noop
   :_
   :a-frame.interceptor-chain/success]
  [:a-frame.std-interceptors/unhandled-error-report
   :a-frame.interceptor-chain/leave
   :a-frame.interceptor-chain/noop
   :_
   :a-frame.interceptor-chain/success]]
```

Should there be an error during system construction you won't get
the system map back directly - instead you will get an errored
promise, with `ex-data` with a `::context` key - which will contain
the interceptor chain context, which has the log.
