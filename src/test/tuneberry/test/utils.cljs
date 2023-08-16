(ns tuneberry.test.utils
  (:require [cljs.core.async :as a :refer [<! go]]
            [cljs.test :refer [async]]
            [lambdaisland.glogi :as log]
            [tuneberry.core :refer [<? tuneberry]]
            [tuneberry.helpers.common :refer [error go-safe]]
            [tuneberry.helpers.http :as http]
            [tuneberry.player :as p]
            [tuneberry.search :refer [search]]
            [tuneberry.test.auth :as auth]))

;;                        .
;;                      .o8
;;  .oooo.o  .ooooo.  .o888oo oooo  oooo  oo.ooooo.
;; d88(  "8 d88' `88b   888   `888  `888   888' `88b
;; `"Y88b.  888ooo888   888    888   888   888   888
;; o.  )88b 888    .o   888 .  888   888   888   888
;; 8""888P' `Y8bod8P'   "888"  `V88V"V8P'  888bod8P'
;;                                         888
;;                                        o888o

;; Must be preserved during code reloads
(defonce tb (atom nil))

(defn init! [client-id access-token]
  (let [s (tuneberry (auth/make-token-fn client-id access-token)
                     :blocking true)]
    (reset! tb s)))

(defn queue-pop [limit]
  (go-safe
    ;; pause the player, so it doesn't modify the queue on its side
    (<? (p/pause-playback @tb))
    (let [queue (<? (p/get-user-queue @tb))
          track-names (map :name (:queue queue))
          qlen (count track-names)
          nr-removed (min qlen limit)]
      (log/config
        :queue-pop/clearing {:track-names track-names :qlen qlen
                             :nr-removed  nr-removed})
      (dotimes [_ nr-removed]
        (<? (p/skip-to-next @tb))
        (log/config :queue-pop/skipped :ok)))))

(def dev (atom nil))

(defn prepare-device! []
  (async done
    (go
      (let [devices (:devices (<! (p/get-available-devices @tb)))]
        (if (empty? devices)
          (js/alert (str
                      "No Spotify devices available.\n\n"
                      "Turn on Spotify player on any device and refresh this page."))
          (do
            (log/config :prepare-device!/found-devices {:devices devices})
            (reset! dev (-> devices first :id))
            (log/config :prepare-device!/device-set {:dev @dev})
            (<? (p/transfer-playback @tb :b/device_ids [@dev]))
            ;; remove a couple of queue items so that you don't get errors from
            ;; trying to put into a full queue
            (<? (queue-pop 5))
            ;; make player more predictable for tests
            (<? (p/toggle-playback-shuffle @tb :state false)))))
      (done))))

;;                                      .o8
;;                                     "888
;; oooo d8b  .oooo.   ooo. .oo.    .oooo888   .ooooo.  ooo. .oo.  .oo.
;; `888""8P `P  )88b  `888P"Y88b  d88' `888  d88' `88b `888P"Y88bP"Y88b
;;  888      .oP"888   888   888  888   888  888   888  888   888   888
;;  888     d8(  888   888   888  888   888  888   888  888   888   888
;; d888b    `Y888""8o o888o o888o `Y8bod88P" `Y8bod8P' o888o o888o o888o

;; Note: it's not a search for the exact artist ("The Guess Who"), but in this
;; case it doesn't hurt.
(defn rand-track []
  (let [artists ["The Who" "Orbital" "Lissie" "Skrillex" "Sigrid" "Queen"
                 "Plaid" "AFX" "Aphex Twin" "Glenn Gould" "Metric" "Muse" "Sia"
                 "the bird and the bee"]
        res-ch (search @tb
                       {:q     (str "artist:" (rand-nth artists))
                        :type  "track"
                        :limit 50
                        :o/sel [:tracks :items]})]
    (go-safe
      (:uri (rand-nth (<? res-ch))))))

;;                    o8o
;;                    `"'
;; ooo. .oo.  .oo.   oooo   .oooo.o  .ooooo.
;; `888P"Y88bP"Y88b  `888  d88(  "8 d88' `"Y8
;;  888   888   888   888  `"Y88b.  888
;;  888   888   888   888  o.  )88b 888   .o8 .o.
;; o888o o888o o888o o888o 8""888P' `Y8bod8P' Y8P

(def Katie-Melua "5uCXJWo3WoXgqv3T1RlAbh")
(def Eva-Cassidy "6fNmOWQzfOVyHLQ2UqUQew")

(defn ex?->vec [thing]
  (if (instance? js/Error thing)
    (->> thing
         ((juxt ex-message ex-data))
         (filterv seq))
    thing))

(defn go-identity [x]
  (go x))

(defn go-const [x]
  #(go x))

(defn go-from-coll-chan [coll]
  (let [ch (a/to-chan! coll)
        f #(go (let [res (<! ch)]
                 (if (nil? res)
                   (error "channel exhausted" {:coll coll})
                   res)))]
    [ch f]))

(defn go-from-coll [coll]
  (second (go-from-coll-chan coll)))

(defn ch->vec [ch]
  (a/reduce conj [] ch))

(defn success [code] {:success true :status code})

(defn failure
  ([code] {:success false :status code})
  ([code msg] (merge (failure code) {:body {:error {:message msg}}})))

(defn err [code & more]
  (http/wrap-error (apply failure code more)))

(defn queue-contains? [uri]
  {:pre [(string? uri) (some? uri)]}
  (go
    (let [queue (<? (p/get-user-queue @tb))
          uris (map :uri (:queue queue))]
      (boolean (some #{uri} uris)))))
