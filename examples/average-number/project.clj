(defproject average-number "0.1.0-SNAPSHOT"
            :dependencies [[org.clojure/clojure "1.9.0"]
                           [org.clojure/clojurescript "1.9.521"]
                           [reagent "0.6.1"]
                           [re-frame "0.9.2"]
                           [compojure "1.5.2"]
                           [ring "1.5.1"]
                           [pneumatic-tubes "0.3.1-SNAPSHOT"]
                           [http-kit "2.2.0"]]

            :min-lein-version "2.5.3"

            :source-paths ["src/clj"]

            :plugins [[lein-cljsbuild "1.1.4"]
                      [lein-figwheel "0.5.10"]]

            :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

            :figwheel {:css-dirs     ["resources/public/css"]
                       :ring-handler average-number.core/handler}

            :cljsbuild {:builds [{:id       "dev"
                        :source-paths       ["src/cljs"]
                                  :figwheel {:on-jsload "average-number.core/mount-root"}
                                  :compiler {:main       average-number.core
                                   :output-to "resources/public/js/compiled/app.js"
                                   :output-dir "resources/public/js/compiled/out"
                                   :asset-path "js/compiled/out"
                                   :source-map-timestamp true}}

                       {:id "min"
                        :source-paths ["src/cljs"]
                        :compiler {:main average-number.core
                                   :output-to "resources/public/js/compiled/app.js"
                                   :optimizations :advanced
                                   :closure-defines {goog.DEBUG false}
                                   :pretty-print false}}]})
