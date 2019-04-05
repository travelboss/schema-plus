(defproject travelboss/schema-plus "0.1.1-SNAPSHOT"
  :description "Adds easy mock generation and builder functions to plumatic/schema definitions"
  :url "http://github.com/travelboss/schema-plus"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [prismatic/schema "1.1.7"]
                 [prismatic/schema-generators "0.1.2"]]
  :deploy-repositories [["releases" :clojars]])
