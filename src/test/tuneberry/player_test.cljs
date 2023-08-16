(ns tuneberry.player-test
  (:require [cljs.core.async :as a :refer [<! go]]
            [cljs.test :refer [async deftest is testing use-fixtures]]
            [tuneberry.core :refer [<? options token-fn tuneberry]]
            [tuneberry.helpers.common]
            [tuneberry.helpers.utils :refer [redo]]
            [tuneberry.player :as p]
            [tuneberry.test.utils :as tst]))

(use-fixtures :once {:before tst/prepare-device!})

(deftest add-item-to-playback-queue-test
  (async done
    (go
      (let [uri (<? (tst/rand-track))
            res (<? (p/add-item-to-playback-queue @tst/tb :uri uri))]
        (is (= res {}))
        (is (true? (<? (tst/queue-contains? uri)))))
      (done))))

(deftest get-available-devices-test
  (async done
    (go
      (let [devs (<? (p/get-available-devices @tst/tb :o/sel [:devices]))]
        (is (some #{@tst/dev} (map :id devs))))
      (done))))

(deftest pause-playback-test
  (async done
    (go
      (<? (p/pause-playback @tst/tb))
      (is (= (:is_playing (<? (p/get-playback-state @tst/tb))) false))
      (let [res (<? (p/pause-playback @tst/tb :o/smart false))]
        (is (= (select-keys res [:status :success]) {:status 403 :success false}))
        (is (= (:is_playing (<? (p/get-playback-state @tst/tb))) false)))
      (done))))

(deftest start-or-resume-playback-test
  (async done
    (go
      (<? (p/start-or-resume-playback @tst/tb))
      (is (= (:is_playing (<? (p/get-playback-state @tst/tb))) true))
      (let [res (<? (p/start-or-resume-playback @tst/tb :o/smart false))]
        (is (= (select-keys res [:status :success]) {:status 403 :success false}))
        (is (= (:is_playing (<? (p/get-playback-state @tst/tb))) true)))
      (done))))

(deftest pause-and-start-test
  (async done
    (go
      (<? (p/pause-playback @tst/tb))
      (is (= (:is_playing (<? (p/get-playback-state @tst/tb))) false))
      (<? (p/start-or-resume-playback @tst/tb {:device_id @tst/dev}))
      (is (= (:is_playing (<? (p/get-playback-state @tst/tb))) true))
      (<? (p/pause-playback @tst/tb {:device_id @tst/dev}))
      (is (= (:is_playing (<? (p/get-playback-state @tst/tb))) false))
      (done))))

(deftest skip-to-next-test
  (async done
    (go
      (let [_ (<? (p/add-item-to-playback-queue @tst/tb :uri (<? (tst/rand-track))))
            top (<? (p/get-user-queue @tst/tb :o/post #(-> % :queue first :name)))
            _ (<? (p/skip-to-next @tst/tb))
            playing (<? (p/get-playback-state @tst/tb :o/sel [:item :name]))]
        (is (= top playing))
        (<? (p/pause-playback @tst/tb)))
      (done))))

(deftest skip-to-next-no-blocking-test
  (async done
    (go
      (let [tb (tuneberry (token-fn @tst/tb)
                          (merge (options @tst/tb) {:blocking false}))
            uri (<? (tst/rand-track))
            ;; Add a random track to the queue so there is something to skip to
            _ (<? (p/add-item-to-playback-queue tb :uri uri))
            ;; Wait for the track to be present in the queue
            _ (<? (redo (partial tst/queue-contains? uri) true?))
            ;; ...to be able to fetch the final top item
            top (<? (p/get-user-queue tb :o/post #(-> % :queue first :name)))
            t1 (<? (p/get-playback-state tb :o/sel [:timestamp]))
            _ (<? (p/skip-to-next tb))
            ;; Wait for the timestamp to change, which means the skipping
            ;; has already taken effect
            _ (<? (redo #(p/get-playback-state tb :o/sel [:timestamp])
                        #(distinct? t1 %)))
            playing (<? (p/get-playback-state tb :o/sel [:item :name]))]
        (is (= top playing))
        (<? (p/pause-playback @tst/tb)))
      (done))))

(deftest play-track-test
  (async done
    (go
      (testing "playing single track"
        (let [uri (<? (tst/rand-track))
              _ (<? (p/start-or-resume-playback @tst/tb {:b/uris [uri]}))
              state (<? (p/get-playback-state @tst/tb))]
          (is (= (:is_playing state) true))
          (is (= (get-in state [:item :uri]) uri))
          (<? (p/pause-playback @tst/tb))))
      (testing "playing three tracks"
        (let [uris (<! (tst/ch->vec (a/merge (repeatedly 3 tst/rand-track))))
              _ (<? (p/start-or-resume-playback @tst/tb {:b/uris uris}))
              state (<? (p/get-playback-state @tst/tb))]
          (is (= (:is_playing state) true))
          (is (= (get-in state [:item :uri]) (first uris)))
          (<? (p/pause-playback @tst/tb))))
      (done))))

(deftest toggle-playback-shuffle-test
  (async done
    (go
      (<? (p/toggle-playback-shuffle @tst/tb :state true))
      (is (= true (:shuffle_state (<? (p/get-playback-state @tst/tb)))))
      (<? (p/toggle-playback-shuffle @tst/tb :state false))
      (is (= false (:shuffle_state (<? (p/get-playback-state @tst/tb)))))
      (done))))
