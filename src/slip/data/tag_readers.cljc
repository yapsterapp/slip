(ns slip.data.tag-readers
  #?(:cljs (:require-macros [slip.data.tag-readers]))
  (:require
   #?(:clj [promisespromises.util.macro :refer [if-cljs]])
   [slip.data.ref-path :refer [ref-path maybe-ref-path]]))

;; see https://github.com/clojure/clojurescript-site/issues/371
;; 3! different versions of the tag-readers are required for:
;; 1. clj compiling cljs
;; 2. clj
;; 3. cljs self-hosted or runtime

#?(:clj
   (defn read-ref-path
     [path]
     (if-cljs
         `(ref-path ~path)

       ;; if we eval the path then we can use var symbols
       ;; in the path - obvs only works on clj
       ;;
       ;; resolving the var avoids stale record issues after
       ;; c.t.n.r/refresh
       ((resolve 'slip.data.ref-path/ref-path) (eval path)))))

#?(:cljs
   (defn ^:export read-ref-path
     [path]
     `(ref-path ~path)))

#?(:clj
   (defn read-maybe-ref-path
     [path]
     (if-cljs
         `(maybe-ref-path ~path)

       ;; if we eval the path then we can use var symbols
       ;; in the path - obvs only works on clj
       ;;
       ;; resolving the var avoids stale record issues after
       ;; c.t.n.r/refresh
       ((resolve 'slip.data.ref-path/maybe-ref-path) (eval path)))))

#?(:cljs
   (defn ^:export read-maybe-ref-path
     [path]
     `(maybe-ref-path ~path)))
