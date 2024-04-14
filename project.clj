(defproject com.github.artbookspirit/tuneberry "1.1.1-SNAPSHOT"
  :description "ClojureScript bindings for Spotify Web API."
  :url "https://github.com/artbookspirit/tuneberry"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojurescript "1.11.60" :scope "provided"]
                 [org.clojure/core.async "1.5.648"]
                 [cljs-http "0.1.46"]
                 [com.lambdaisland/glogi "1.3.169"]]
  :source-paths ["src/main"]
  :repositories {"clojars" {:url           "https://clojars.org/repo"
                            :sign-releases false}})
