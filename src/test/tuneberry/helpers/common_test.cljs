(ns tuneberry.helpers.common-test
  (:require [cljs.core.async :refer [<! go]]
            [cljs.test :refer [are async deftest is testing]]
            [tuneberry.helpers.common :as hlp :refer [go-safe]]
            [tuneberry.test.utils :refer [ex?->vec]]))

(deftest error-test
  (is (= (ex?->vec (hlp/error "msg")) ["msg"]))
  (is (= (ex?->vec (hlp/error "msg" {:n 42})) ["msg" {:n 42}])))

(deftest error?-test
  (are [x expected] (= (hlp/error? x) expected)
                    nil false
                    "foo" false
                    {:foo "bar"} false
                    (js/Error "msg") true
                    (hlp/error "msg") true
                    (hlp/error "msg" {:n 42}) true))

(deftest throw-error-test
  (testing "forwards a non-error"
    (is (= (hlp/throw-error 42) 42)))
  (testing "throws an error"
    (try (hlp/throw-error (js/Error "msg"))
         (assert false "should not execute")
         (catch js/Error e
           (is (= (ex-message e) "msg"))))))

(deftest deep-merge-test
  (are [maps expected]
    (= (apply hlp/deep-merge maps) expected)
    [{} {}] {}
    [nil {}] {}
    [{} nil] {}
    [nil nil] nil
    [{:a 1} {}] {:a 1}
    [{} {:a 1}] {:a 1}
    [{:a 1} {:b 2} {:c 3}] {:a 1 :b 2 :c 3}
    [{:a 1} {:a 5}] {:a 5}
    [{:x {:a 1}} {:x {:b 2}}] {:x {:a 1 :b 2}}
    [{:x {:y {:a 1}}} {:x {:y {:b 2}}}] {:x {:y {:a 1 :b 2}}}
    [{:x {:a 1}} {:x nil}] {:x {:a 1}}
    [{:x {:a 1 :b 2}} {:x {:b 3}}] {:x {:a 1 :b 3}}))

(deftest deep-merge-err-test
  (testing "throwing error when trying to associate with string"
    (is (thrown? js/Error (hlp/deep-merge {:x "x"} {:x {:y 1}})))
    (is (thrown? js/Error (hlp/deep-merge {:x {:y 1}} {:x "x"})))))

(deftest binary-geometric-seq-test
  (is (= (take 5 ((hlp/binary-geometric-seq 100))) [100 200 400 800 1600]))
  (is (= (take 5 ((hlp/binary-geometric-seq 500))) [500 1000 2000 4000 8000])))

;; ooo. .oo.  .oo.    .oooo.    .ooooo.  oooo d8b  .ooooo.   .oooo.o
;; `888P"Y88bP"Y88b  `P  )88b  d88' `"Y8 `888""8P d88' `88b d88(  "8
;;  888   888   888   .oP"888  888        888     888   888 `"Y88b.
;;  888   888   888  d8(  888  888   .o8  888     888   888 o.  )88b
;; o888o o888o o888o `Y888""8o `Y8bod8P' d888b    `Y8bod8P' 8""888P'

(deftest nothrow-test
  (is (= (hlp/nothrow 10) 10))
  (is (= (hlp/nothrow (+ 2 2)) 4))
  (is (= (ex?->vec (hlp/nothrow (throw (hlp/error "msg" {:foo :bar}))))
         ["msg" {:foo :bar}])))

(deftest go-safe-test
  (async done
    (go
      (is (= (<! (go-safe (+ 5 5))) 10))
      (is (= (ex?->vec (<! (go-safe (throw (hlp/error "msg" {:foo :bar})))))
             ["msg" {:foo :bar}]))
      (done))))
