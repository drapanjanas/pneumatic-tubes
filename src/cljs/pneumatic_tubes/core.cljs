(ns pneumatic-tubes.core
  (:require-macros [cljs.core.async.macros :refer [go-loop]])
  (:require [cljs.core.async :refer [close! chan <! put!]]
            [clojure.string :as str]
            [cognitect.transit :as t]))

(def ^:private instances (atom {}))

(def r (t/reader :json))
(def w (t/writer :json))

(defn log [& msg] (.log js/console (apply str msg)))
(defn error [& msg] (.error js/console (apply str msg)))
(defn warn [& msg] (.warn js/console (apply str msg)))

(defn increasing-random-timeout [min-timout]
  (fn [retries] (rand (max (* retries 1000) min-timout))))

(def default-config
  {:web-socket-impl  js/WebSocket
   :out-queue-size   10
   :backoff-strategy (increasing-random-timeout 5000)})
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

(defn- get-instance [tube-spec]
  (get @instances (tube-id tube-spec)))

(defn- make-on-open [in-chan]
  (fn []
    (put! in-chan [:tube/on-create])))

(defn- make-on-message [in-chan]
  (fn [msg]
    (let [event-v (t/read r (.-data msg))]
      (put! in-chan [:tube/on-message event-v]))))

(defn- make-on-close [in-chan]
  (fn [close-event]
    (put! in-chan [:tube/on-close (.-code close-event)])))

(defn- make-new-socket [url ws-impl on-open on-message on-close]
  (if-let [socket (ws-impl. url)]
    (do
      (set! (.-onopen socket) on-open)
      (set! (.-onmessage socket) on-message)
      (set! (.-onclose socket) on-close)
      socket)
    (error "WebSocket connection failed. url: " url)))

(defn- start-send-loop! [socket out-queue]
  (go-loop
    [event (<! out-queue)]
    (when event
      (.send socket (t/write w event))
      (recur (<! out-queue)))))

(defn- create-instance [url on-receive on-connect on-disconnect on-connect-failed
                        {ws-impl :web-socket-impl queue-size :out-queue-size backoff :backoff-strategy}]
  (let [in-chan (chan)
        on-open (make-on-open in-chan)
        on-message (make-on-message in-chan)
        on-close (make-on-close in-chan)
        instance (atom {:socket    (make-new-socket url ws-impl on-open on-message on-close)
                        :out-queue (chan)
                        :retries   0
                        :connected false
                        :destroyed false})]
    (go-loop
      [event-v (<! in-chan)]
      (when event-v
        (let [ev-type (first event-v)]
          (cond

            (= ev-type :tube/on-create)
            (let [{:keys [socket out-queue]} @instance]
              (log "Created tube on " url)
              (swap! instance merge {:connected true
                                     :retries 0})
              (start-send-loop! socket out-queue)
              (on-connect))

            (= ev-type :tube/on-message)
            (on-receive (second event-v))

            (= ev-type :tube/on-close)
            (let [code (second event-v)
                  {:keys [connected destroyed retries out-queue]} @instance]
              (swap! instance assoc :connected false)
              (close! out-queue)
              (if connected
                (on-disconnect code)
                (on-connect-failed code))
              (if destroyed
                (do
                  (log "Destroyed tube on " url)
                  (close! in-chan))
                (js/setTimeout (fn []
                                 (log "Reconnect " retries " : " url)
                                 (swap! instance merge {:socket    (make-new-socket url ws-impl on-open on-message on-close)
                                                        :out-queue (chan)
                                                        :retries   (inc retries)}))
                               (backoff retries))))))
        (recur (<! in-chan))))
    instance))

(defn- destroy-instance [instance]
  (let [socket (:socket @instance)]
    (swap! instance assoc :destroyed true)
    (.close socket)
    instance))

(defn dispatch [tube-spec event-v]
  "Sends the event to some tube"
  (let [instance (get-instance tube-spec)
        ch (:out-queue @instance)]
    (if ch
      (put! ch event-v)
      (throw (js/Error. (str "Tube for " (:url tube-spec) " is not started!"))))))

(defn destroy! [tube-spec]
  "Destroys tube. On server this will trigger :tube/on-destroy event."
  (when-let [instance (get-instance tube-spec)]
    (swap! instances dissoc (tube-id tube-spec))
    (destroy-instance instance)))

(defn create!
  "Creates a tube. On server side this will trigger :tube/on-create event"
  ([tube-spec]
   (create! tube-spec nil))
  ([tube-spec params]
   (destroy! tube-spec)
   (let [param-str (str/join "&" (for [[k v] params] (str (name k) "=" v)))
         base-url (:url tube-spec)
         {:keys [on-receive on-disconnect on-connect on-connect-failed config]} tube-spec
         url (if (empty? param-str) base-url (str base-url "?" param-str))
         instance (create-instance url on-receive on-connect on-disconnect on-connect-failed config)]
     (swap! instances assoc base-url instance))))

