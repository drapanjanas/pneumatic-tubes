(ns pneumatic-tubes.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [close! chan <! put!]]
            [clojure.string :as str]
            [cognitect.transit :as t]))

(def ^:private instances (atom {}))

(defn log [& msg] (.log js/console (apply str msg)))
(defn error [& msg] (.error js/console (apply str msg)))
(defn warn [& msg] (.warn js/console (apply str msg)))

(defn increasing-random-timeout [min-timout]
  (fn [retries] (rand (max (* retries 1000) min-timout))))

(def default-config
  {:web-socket-impl  js/WebSocket
   :out-queue-size   10
   :backoff-strategy (increasing-random-timeout 5000)
   :read-handlers {}
   :write-handlers {}})

(defn- noop [])

(defn tube
  "Creates the spec of a tube. Parameters:
    url: web socket connection url.
    on-receive: function to process received events, takes event as parameter
    on-connect: function will be called when connection is successfully established
    on-disconnect: function will be called when connection lost or tube is destroyed by user, acceps code as parameter
    on-connect-failed: function will be called when attempt to connect to server failed, accepts code as paremeter
    config: optional configuration for the tube, see default-config"
  ([url on-receive]
   (tube url on-receive noop noop noop default-config))
  ([url on-receive config]
   (tube url on-receive noop noop noop config))
  ([url on-receive on-connect on-disconnect on-connect-failed]
   (tube url on-receive on-connect on-disconnect on-connect-failed default-config))
  ([url on-receive on-connect on-disconnect on-connect-failed config]
   {:url               url
    :on-receive        on-receive
    :on-disconnect     on-disconnect
    :on-connect        on-connect
    :on-connect-failed on-connect-failed
    :config            (merge default-config config)}))

(defn- tube-id [tube-spec]
  (:url tube-spec))

(defn- get-tube-instance
  ([tube-spec]
   (get-tube-instance @instances tube-spec))
  ([all-instances tube-spec]
   (get all-instances (tube-id tube-spec))))

(defn- init-tube-instance!
  "Creates new instance if not exist or updates existing"
  [tube-spec socket out-queue]
  (swap! instances
         #(let [inst (get-tube-instance % tube-spec)]
            (assoc % (tube-id tube-spec)
                     {:socket    socket
                      :out-queue out-queue
                      :retries   (if inst (inc (:retries inst)) 0)
                      :connected false
                      :destroyed (if inst (:destroyed inst) false)}))))

(defn- rm-tube-instance! [tube-spec]
  (swap! instances dissoc (tube-id tube-spec)))

(defn- mark-tube-destroyed! [tube-spec]
  (swap! instances assoc-in [(tube-id tube-spec) :destroyed] true))

(defn- mark-tube-connected! [tube-spec]
  (swap! instances assoc-in [(tube-id tube-spec) :connected] true))

(defn dispatch [tube event-v]
  "Sends the event to some tube"
  (let [ch (:out-queue (get-tube-instance tube))]
    (if ch
      (put! ch event-v)
      (throw (js/Error. (str "Tube for " (:url tube) " is not started!"))))))

(defn- start-send-loop! [w socket out-queue]
  (go-loop
    [event (<! out-queue)]
    (when event
      (.send socket (t/write w event))
      (recur (<! out-queue)))))

(defn create!
  "Creates a tube. On server side this will trigger :tube/on-create event"
  ([tube]
   (create! tube nil))
  ([tube params]
   (let [param-str (str/join "&" (for [[k v] params] (str (name k) "=" v)))
         base-url (:url tube)
         {:keys [on-receive on-disconnect on-connect on-connect-failed config]} tube
         {ws-impl :web-socket-impl queue-size :out-queue-size backoff :backoff-strategy
          :keys [read-handlers write-handlers]} config
         url (if (empty? param-str) base-url (str base-url "?" param-str))
         out-queue (chan queue-size)
         w (t/writer :json {:handlers write-handlers})
         r (t/reader :json {:handlers read-handlers})]
     (if-let [socket (ws-impl. url)]
       (do
         (set! (.-onopen socket) (fn []
                                   (log "Created tube on " url)
                                   (mark-tube-connected! tube)
                                   (start-send-loop! w socket out-queue)
                                   (on-connect)))
         (set! (.-onclose socket) (fn [close-event]
                                    (let [instance (get-tube-instance tube)
                                          {:keys [out-queue retries connected destroyed]} instance]
                                      (close! out-queue)
                                      (if connected
                                        (on-disconnect (.-code close-event))
                                        (on-connect-failed (.-code close-event)))
                                      (if destroyed
                                        (do
                                          (rm-tube-instance! tube)
                                          (log "Destroyed tube on " url))
                                        (js/setTimeout (fn []
                                                         (log "Reconnect " retries " : " url)
                                                         (create! tube params)) (backoff retries))))))
         (set! (.-onmessage socket) (fn [msg]
                                      (let [event-v (t/read r (.-data msg))]
                                        (on-receive event-v))))
         (init-tube-instance! tube socket out-queue))
       (error "WebSocket connection failed. url: " url)))))

(defn destroy! [tube]
  "Destroys tube. On server this will trigger :tube/on-destroy event."
  (let [socket (:socket (get-tube-instance tube))]
    (mark-tube-destroyed! tube)
    (.close socket)))
