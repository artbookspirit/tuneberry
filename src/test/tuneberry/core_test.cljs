(ns tuneberry.core-test
  (:require [cljs.core.async :refer [<! chan go put!]]
            [cljs.test :refer [are async deftest is testing]]
            [tuneberry.core :refer [<? options token-fn tuneberry]]
            [tuneberry.helpers.common :as hlp]
            [tuneberry.test.utils :as tst]))

(deftest tuneberry-test
  (async done
    (go
      (let [base-opts hlp/default-option-map
            token-fn (tst/go-const "dummy token")]
        (testing "initialization with bare token function"
          (is (= (tuneberry token-fn) {:token-fn token-fn :config base-opts})))
        (testing "initialization with specific options"
          (let [opts {:api-url "foo://bar" :foo {:bar 7}}
                tb (tuneberry token-fn opts)]
            (is (= tb {:token-fn token-fn :config (merge base-opts opts)}))))
        (testing "initialization with token value"
          (let [tb (tuneberry "dummy token")]
            (is (= (:config tb) base-opts))
            (is (= (<! ((:token-fn tb))) "dummy token")))))
      (done))))

(deftest options-test
  (are [tb expected] (= (options tb) expected)
                     {} nil
                     {:config {:n 42}} {:n 42}))

(deftest token-fn-test
  (async done
    (go
      (is (= (<! ((token-fn (tuneberry (tst/go-const "a-token"))))) "a-token"))
      (done))))

;; ooo. .oo.  .oo.    .oooo.    .ooooo.  oooo d8b  .ooooo.   .oooo.o
;; `888P"Y88bP"Y88b  `P  )88b  d88' `"Y8 `888""8P d88' `88b d88(  "8
;;  888   888   888   .oP"888  888        888     888   888 `"Y88b.
;;  888   888   888  d8(  888  888   .o8  888     888   888 o.  )88b
;; o888o o888o o888o `Y888""8o `Y8bod8P' d888b    `Y8bod8P' 8""888P'

(deftest <?-test
  (async done
    (go
      (testing "returns a non-error"
        (let [ch (chan)]
          (put! ch 42)
          (is (= (<? ch) 42))))
      (testing "throws an error"
        (let [ch (chan)]
          (put! ch (js/Error "error msg"))
          (try
            (<? ch)
            (assert false "should not execute")
            (catch js/Error e
              (is (= (ex-message e) "error msg"))))))
      (done))))
