(ns tuneberry.player
  (:require [tuneberry.helpers.framework :refer [request!]]))

(defn get-user-queue
  "Returns the contents of the user's queue. No params.

  If there are no active devices, the returned queue will be empty. After
  transferring playback to a device or modifying the queue, it takes some time
  before its current value starts to be returned.

  https://developer.spotify.com/documentation/web-api/reference/get-queue"
  [tb & {:as keyvals}]
  (request! tb
            :method :get
            :path "/me/player/queue"
            :msg :get-user-queue/getting
            :mixed keyvals))

(defn add-item-to-playback-queue
  "Adds an item with the specified uri to the end of the user's playback queue.
  Params: uri, device_id.

  uri must identify a track ('spotify:track:<track id>') or an episode
  ('spotify:episode:<episode id>').

  Blocking: if enabled, the result is not returned until the given item appears
  in the user's queue which is obtained with additional API calls. Note that
  the item may already be present in the queue, so this check is not
  comprehensive. However, it is enough in the typical case.

  https://developer.spotify.com/documentation/web-api/reference/add-to-queue"
  [tb & {:keys [uri] :as keyvals}]
  {:pre [(string? uri) (some? uri)]}
  (request! tb
            :method :post
            :path "/me/player/queue"
            :msg :add-item-to-playback-queue/adding
            :mixed keyvals
            :opts {:block-until {:opts     {:post #(map :uri (:queue %))}
                                 :state-fn get-user-queue
                                 :pred     #(some #{uri} %)}}))

(defn get-available-devices
  "Returns a list of devices currently running Spotify. No params.

  https://developer.spotify.com/documentation/web-api/reference/get-a-users-available-devices"
  [tb & {:as keyvals}]
  (request! tb
            :method :get
            :path "/me/player/devices"
            :msg :get-available-devices/getting
            :mixed keyvals))

(defn get-playback-state
  "Returns information about the current playback status, including item,
  progress and active device. Params: market, additional_types.

  The timestamp output parameter seems to contain the time the current track
  started playing and can be used to check if the Skip To Next/Skip To Previous
  track operation has already taken effect.

  https://developer.spotify.com/documentation/web-api/reference/get-information-about-the-users-current-playback"
  [tb & {:as keyvals}]
  (request! tb
            :method :get
            :path "/me/player"
            :msg :get-playback-state/getting
            :mixed keyvals))

(defn get-recently-played-tracks
  "Returns recently played tracks (not episodes). Params: limit, before, after.

  https://developer.spotify.com/documentation/web-api/reference/get-recently-played"
  [tb & {:as keyvals}]
  (request! tb
            :method :get
            :path "/me/player/recently-played"
            :msg :get-recently-played-tracks/getting
            :mixed keyvals))

(defn pause-playback
  "Pauses playback. Params: device_id.

  Error suppression: if playback is already paused, the server returns status
  403 with message 'Player command failed: Restriction violated' and reason
  'UNKNOWN'. This error is ignored, i.e. the resulting map is returned directly,
  instead of being wrapped in a js/Error object (the <? macro throws no
  exception).

  Blocking: if enabled, no result is returned until the state of the playback,
  fetched by additional API requests, contains the is_playing output parameter
  as false.

  https://developer.spotify.com/documentation/web-api/reference/pause-a-users-playback"
  [tb & {:as keyvals}]
  (request! tb
            :method :put
            :path "/me/player/pause"
            :msg :pause-playback/pausing
            :mixed keyvals
            :opts {:suppress    [[403 #"Restriction violated"]]
                   :block-until {:opts     {:sel [:is_playing]}
                                 :state-fn get-playback-state
                                 :pred     false?}}))

(defn start-or-resume-playback
  "Starts/resumes playback. Params: device_id, b/context_uri, b/uris, b/offset,
  b/position_ms.

  Error suppression: as for pause-playback, except error 403 is suppressed in
  case of double start.

  Blocking: like for pause-playback, except that is_playing is tested for true.
  Also, if either b/context_uri or b/uris is given, an additional check is made
  to see if b/context_uri or one of b/uris is actually being played.

  https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback"
  [tb & {:keys [b/context_uri b/uris] :as keyvals}]
  {:pre [(not (and context_uri uris))]}
  (let [expected-uris (cond
                        (some? context_uri) [context_uri]
                        (some? uris) uris)
        uri-playing? (fn [{:keys [item context] :as _playback-state}]
                       (let [uri (:uri (cond context_uri context
                                             uris item))]
                         (some #{uri} expected-uris)))]
    (request! tb
              :method :put
              :path "/me/player/play"
              :msg :start-or-resume-playback/starting
              :mixed keyvals
              :opts {:suppress    [[403 #"Restriction violated"]]
                     :block-until {:state-fn get-playback-state
                                   :pred     #(and (true? (:is_playing %))
                                                   (or (nil? expected-uris)
                                                       (uri-playing? %)))}})))

(defn skip-to-next
  "Skips to the next item in the user's queue. Params: device_id.

  Blocking: if enabled, no result is returned until the playback state (fetched
  by additional API requests) contains a different timestamp than before the
  'Skip To Next' API command was called. (See also: get-playback-state.)

  Note that a race is possible where at the same time: 1. Spotify player skips
  to the next track because the current one ends, 2. this function is called.
  Therefore, if you want to be sure that exactly one track was skipped, you
  should work with a paused player (see pause-playback).

  https://developer.spotify.com/documentation/web-api/reference/skip-users-playback-to-next-track"
  [tb & {:as keyvals}]
  (request! tb
            :method :post
            :path "/me/player/next"
            :msg :skip-to-next/skipping
            :mixed keyvals
            :opts {:block-until {:opts          {:sel [:timestamp]}
                                 :state-fn      get-playback-state
                                 :pred          #(apply distinct? %)
                                 :initial-poll? true}}))

(defn skip-to-previous
  "Skips to the previous item in the user's queue. Params: device_id.

  Blocking: like for skip-to-next (see also notes to that function about
  possible race condition).

  https://developer.spotify.com/documentation/web-api/reference/skip-users-playback-to-previous-track"
  [tb & {:as keyvals}]
  (request! tb
            :method :post
            :path "/me/player/previous"
            :msg :skip-to-previous/skipping
            :mixed keyvals
            :opts {:block-until {:opts          {:sel [:timestamp]}
                                 :state-fn      get-playback-state
                                 :pred          #(apply distinct? %)
                                 :initial-poll? true}}))

(defn transfer-playback
  "Transfers playback to the specified device and determines whether it should
  start playing. Despite the array parameter b/device_ids, only one device is
  supported. Params: b/device_ids, b/play.

  Blocking: if enabled, no result is returned until the list of available
  devices, fetched by additional API calls, contains the specified device with
  the 'is_active' parameter set to true. The playback queue is returned
  properly only when the device is active.

  https://developer.spotify.com/documentation/web-api/reference/transfer-a-users-playback"
  [tb & {:keys [b/device_ids] :as keyvals}]
  {:pre [(every? string? device_ids)]}
  (let [select-device (fn [res]
                        (->> res
                             :devices
                             (filter #(= (:id %) (first device_ids)))
                             first))]
    (request! tb
              :method :put
              :path "/me/player"
              :msg :transfer-playback/transferring
              :mixed keyvals
              :opts {:block-until {:opts     {:post select-device}
                                   :state-fn get-available-devices
                                   :pred     :is_active}})))

(defn toggle-playback-shuffle
  "Toggles playback shuffle. Params: state, device_id.

  Blocking: if enabled, no result is returned until the shuffle state (fetched
  by additional API requests) has the same value as the state input parameter.

  https://developer.spotify.com/documentation/web-api/reference/toggle-shuffle-for-users-playback"
  [tb & {:keys [state] :as keyvals}]
  {:pre [(boolean? state)]}
  (request! tb
            :method :put
            :path "/me/player/shuffle"
            :msg :toggle-playback-shuffle/toggling
            :mixed keyvals
            :opts {:block-until {:opts     {:sel [:shuffle_state]}
                                 :state-fn get-playback-state
                                 :pred     (partial = state)}}))
