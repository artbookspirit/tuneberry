(ns tuneberry.users-test
  (:require [cljs.core.async :refer [go]]
            [cljs.test :refer [async deftest is]]
            [tuneberry.core :refer [<?]]
            [tuneberry.test.utils :as tst]
            [tuneberry.users :as users]))

(deftest get-current-user-profile-test
  (async done
    (go
      (let [profile (<? (users/get-current-user-profile @tst/tb))]
        (is (some #{"@"} (:email profile))))
      (done))))
