(ns tuneberry.search-test
  (:require [cljs.core.async :refer [go]]
            [cljs.test :refer [async deftest is testing]]
            [tuneberry.core :refer [<?]]
            [tuneberry.search :refer [search]]
            [tuneberry.test.utils :as tst]))

(deftest search-test
  (async done
    (go
      (testing "searching for album with the query as string"
        (let [album (<? (search @tst/tb
                                :q "artist:Aphex Twin album:Syro"
                                :type "album"
                                :o/sel [:albums :items 0]))]
          (is (= (get-in album [:artists 0 :name]) "Aphex Twin"))
          (is (= (:name album) "Syro"))
          (is (= (:total_tracks album) 12))))
      (testing "searching for album tracks with the query as map"
        (let [tracks (<? (search @tst/tb
                                 :q {:artist "Aphex Twin" :album "Syro"}
                                 :type "track"
                                 :o/sel [:tracks :items]))]
          (is (= (count tracks) 12))
          (is (= (->> tracks
                      (filter #(= (:track_number %) 10))
                      first
                      :name)
                 "PAPAT4 [155][pineal mix]"))))
      (done))))
