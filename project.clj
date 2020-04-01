(defproject pneumatic-tubes "0.3.1"
  :description "WebSocket based transport of events between re-frame app and server"
  :url "https://github.com/rschmukler/pneumatic-tubes"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-RC1"]
                 [org.clojure/clojurescript "1.9.946"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/core.async "0.3.442" :scope "provided"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [com.cognitect/transit-clj "0.8.300"]
                 [http-kit "2.2.0" :scope "provided"]]

  :scm {:name "git"
        :url  "https://github.com/rschmukler/pneumatic-tubes"}

  :min-lein-version "2.5.3"
  :source-paths ["src/clj"]
  :java-cmd "/usr/lib/jvm/java-8-openjdk/bin/java"
  :plugins [[lein-cljsbuild "1.1.4"]]
  :hooks [leiningen.cljsbuild]

  :cljsbuild {:builds [{:source-paths ["src/cljs"]
                        :compiler     {:output-to "target/pneumatic-tubes.js"}
                        :jar          true}]})
