(defproject group-chat "0.1.0-SNAPSHOT"
            :dependencies [[org.clojure/clojure "1.9.0"]
                           [org.clojure/clojurescript "1.9.521"]
                           [reagent "0.6.1"]
                           [re-frame "0.9.2"]
                           [compojure "1.5.2"]
                           [ring "1.5.1"]
                           [ring/ring-defaults "0.2.1"]
                           [pneumatic-tubes "0.3.1-SNAPSHOT"]
                 [http-kit "2.2.0"]
                 [environ "1.1.0"]
                 [ch.qos.logback/logback-classic "1.1.7"]
                 [com.datomic/datomic-free "0.9.5407" :exclusions [com.google.guava/guava org.slf4j/slf4j-nop joda-time org.slf4j/slf4j-log4j12]]]

            :min-lein-version "2.5.3"

            :source-paths ["src/clj"]

            :plugins [[lein-cljsbuild "1.1.4"]
                      [lein-figwheel "0.5.10"]]

            :main group-chat.server

            :uberjar-name "group-chat-standalone.jar"

            :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

            :figwheel {:css-dirs ["resources/public/css"]
             :ring-handler       group-chat.core/app-with-reload}

            :cljsbuild {:builds [{:id "dev"
                        :source-paths ["src/cljs"]
                        :figwheel     {:on-jsload "group-chat.core/mount-root"}
                        :compiler     {:main                 group-chat.core
                                       :output-to            "resources/public/js/compiled/app.js"
                                       :output-dir           "resources/public/js/compiled/out"
                                       :asset-path           "js/compiled/out"
                                       :source-map-timestamp true}}

                       {:id           "min"
                        :source-paths ["src/cljs"]
                        :compiler     {:main            group-chat.core
                                       :output-to       "resources/public/js/compiled/app.js"
                                       :optimizations   :advanced
                                       :closure-defines {goog.DEBUG false}
                                       :pretty-print    false}}]}

            :profiles {:dev {:dependencies [[com.cemerick/piggieback "0.2.1"]
                                            [figwheel-sidecar "0.5.10"]]}
             :uberjar       {:prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :aot        :all}}
            :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]})
