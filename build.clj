(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b] ; for b/git-count-revs
            [org.corfield.build :as bb]
            [clojure.string :as str]))

(def lib 'com.github.yapsterapp/slip)
(def web-url "https://github.com/yapsterapp/slip")
(def scm-url "git@github.com:yapsterapp/slip.git")
(def version (format "0.0.%s" (b/git-count-revs nil)))

(defn sha
  "the git sha is needed to tag a release in the pom.xml for cljdocs"
  [{:keys [dir path] :or {dir "."}}]
  (-> {:command-args (cond-> ["git" "rev-parse" "HEAD"]
                       path (conj "--" path))
       :dir (.getPath (b/resolve-path dir))
       :out :capture}
      b/process
      :out
      str/trim))

(defn test "Run the tests." [opts]
  (bb/run-tests opts))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      (assoc :lib lib
             :version version
             :scm {:tag (sha nil)
                   :connection (str "scm:git:" scm-url)
                   :developerConnection (str "scm:git:" scm-url)
                   :url web-url})
      (bb/run-tests)
      (bb/clean)
      (bb/jar)))

(defn install "Install the JAR locally." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/install)))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/deploy)))
