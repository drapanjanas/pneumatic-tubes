(ns pneumatic-tubes.httpkit
  (:use pneumatic-tubes.core
        org.httpkit.server)
  (:require [cognitect.transit :as t])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)))

(def ^:dynamic *string-encoding* "UTF-8")

(defn- write-str
  "Writes a value to a string."
  ([o type] (write-str o type {}))
  ([o type opts]
   (let [out (ByteArrayOutputStream.)
         writer (t/writer out type opts)]
     (t/write writer o)
     (.toString out *string-encoding*))))

(defn read-str
  "Reads a value from a decoded string"
  ([s type] (read-str s type {}))
  ([^String s type opts]
   (let [in (ByteArrayInputStream. (.getBytes s *string-encoding*))]
     (t/read (t/reader in type opts)))))

(defn- send-fn [ch opts]
  (fn [data]
    (when (open? ch)
      (send! ch (write-str data :json opts)))))

(defn websocket-handler
  "Creates WebSocket request handler, use it in your compojure routes.

  Opts includes `:read-handlers` and `:write-handlers` which will be used by transit's read
  and write respectively."
  ([receiver]
   (websocket-handler receiver {} {}))
  ([receiver tube-data]
   (websocket-handler receiver tube-data {}))
  ([receiver tube-data opts]
   (let [{:keys [read-handlers
                 write-handlers]} opts]
     (fn [request]
       (with-channel
         request ch
         (let [tube-id (add-tube! (send-fn ch {:handlers write-handlers}) tube-data)]
           (on-close ch (fn [_]
                          (let [destroyed-tube (get-tube tube-id)]
                            (rm-tube! tube-id)
                            (receive receiver destroyed-tube [:tube/on-destroy]))))
           (on-receive ch (fn [message]
                            (receive receiver (get-tube tube-id) (read-str message :json {:handlers read-handlers}))))
           (receive receiver (get-tube tube-id) [:tube/on-create])))))))
