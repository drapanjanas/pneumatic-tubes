(ns average-number.core
  (:use org.httpkit.server
        [compojure.core :only [GET POST defroutes routes]]
        [compojure.handler :only [api]]
        [ring.util.response :only [file-response response]]
        [pneumatic-tubes.core :only [receiver transmitter dispatch]]
        [pneumatic-tubes.httpkit :only [websocket-handler]]))

(def tx (transmitter))
(def dispatch-to (partial dispatch tx))

(def numbers (atom {}))

(defn- average [numbers]
       (let [cnt (count numbers)]
         (double (/ (apply + numbers) (if (pos? cnt) cnt 1)))))

(defn- update-number! [client-id num]
       (let [nums (swap! numbers assoc client-id num)]
            (dispatch-to :all [:average-changed (average (vals nums))])))

(defn- remove-number! [client-id]
       (let [nums (swap! numbers dissoc client-id)]
            (dispatch-to :all [:average-changed (average (vals nums))])))

(def rx (receiver
          {:tube/on-create
           (fn [from _]
             (update-number! (:tube/id from) 0)
             from)

           :tube/on-destroy
           (fn [from _]
             (remove-number! (:tube/id from))
             from)

           :number-changed
           (fn [from [_ num]]
             (update-number! (:tube/id from) (read-string num))
             from)}))

(defroutes handler
           (GET "/" [] (file-response "index.html" {:root "resources/public"}))
           (GET "/ws" [] (websocket-handler rx)))


