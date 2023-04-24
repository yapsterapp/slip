# slip

[clip](https://github.com/juxt/clip)'s one-trick cousin

slip is a Clojure+Script micro-library which transforms an object system
specification into an [a-frame](https://github.com/yapsterapp/a-frame)
data-driven interceptor-chain and runs that interceptor-chain for fun and profit

## system specification



## lifecycle methods

for each `<factory-key>` there are a series of lifecycle methods

``` clojure
(defmulti start (fn [<key> <data>]))
(defmulti stop (fn [<key> <object>]))
```


- system
- extract refs for topo sort
- topo sort system 
- convert to interceptor-chain data 
- run interceptor chain
