(ns tuneberry.helpers.middleware-test
  (:require [cljs.core.async :refer [<! >! chan close! go timeout]]
            [cljs.test :refer [async deftest is testing]]
            [tuneberry.helpers.common :as hlp :refer [error]]
            [tuneberry.helpers.middleware :as mid]
            [tuneberry.test.utils :as tst :refer [err]]))

(deftest with-mappers-test
  (let [mapper1 (fn [req] (fn [res] (update res :foo conj (:x1 req))))
        mapper2 (fn [req] (fn [res] (update res :foo conj (:x2 req))))
        handler ((mid/with-mappers mapper1 mapper2) tst/go-identity)
        res (handler {:x1 2 :x2 3 :foo [1]})]
    (async done
      (go
        (is (= (:foo (<! res)) [1 2 3]))
        (done)))))

(deftest with-token-test
  (async done
    (go
      (testing "token added to req if retrieved successfully"
        (let [req {:token-fn (tst/go-const "dummy-token")}
              handler (mid/with-token tst/go-identity)]
          (is (= (:token (<! (handler req))) "dummy-token"))))
      (testing "token error returned and handler not called"
        (let [req {:token-fn (tst/go-const (error "no token"))}
              handler (mid/with-token #(assert false "should never be called"))]
          (is (= (tst/ex?->vec (<! (handler req))) ["no token"]))))
      (done))))

(deftest with-blocking-test
  (letfn [(request [& {:keys [handler state-fn pred initial-poll?]
                       :or   {handler       (tst/go-const :res)
                              state-fn      (tst/go-const :ok)
                              pred          (partial = :ok)
                              initial-poll? false}}]
            (let [req {:wait-fn        timeout
                       :max-poll       3
                       :poll-delays-fn (constantly (repeat 100))
                       :block-until    (-> {:state-fn      state-fn
                                            :pred          pred
                                            :initial-poll? initial-poll?})}]
              (go (tst/ex?->vec (<! ((mid/with-blocking handler) req))))))]
    (async done
      (go
        (is (= (<! (request)) :res)
            "successful polling")
        (is (= (<! (request :state-fn (tst/go-from-coll [:bad :ok]))) :res)
            "successful polling for the second time")
        (is (= (<! (request :state-fn (tst/go-const :bad)))
               ["polling limit reached" {:nr-attempts 3 :last-result :bad}])
            "failed blocking reported with correct msg and state")
        (is (= (<! (request :initial-poll? true :pred #(apply = %))) :res)
            "successful polling with initial check")
        (is (= (<! (request :initial-poll? true :pred #(apply = %)
                            :state-fn (tst/go-from-coll [:x :a :b :c])))
               ["polling limit reached" {:nr-attempts 3 :last-result :c}])
            "failed blocking after 3 attempts with initial check")
        (is (= (<! (request :state-fn (tst/go-const (error "err")))) ["err"])
            "error from state function correctly returned")
        ;; return error once to check if it is detected during the initial check
        (is (= (<! (request :state-fn (tst/go-from-coll [(error "err") :ok :ok])
                            :initial-poll? true)) ["err"])
            "error from state function correctly returned for initial check")
        (is (= (<! (request :pred #(throw (error "pred err")))) ["pred err"])
            "error thrown by predicate correctly returned")
        (is (= (<! (request :handler (tst/go-const (error "err")))) ["err"])
            "error from handler correctly returned")
        (done)))))

(deftest with-retry-test
  (letfn [(request [handler-responses]
            (let [handler (tst/go-from-coll handler-responses)
                  delays-ch (chan 10)
                  options {:max-retry       2
                           :retry           [[500 #"oo$"] 502]
                           :retry-delays-fn (hlp/binary-geometric-seq 100)
                           :wait-fn         #(go (>! delays-ch %))}]
              (go
                (let [res (<! ((mid/with-retry handler) options))]
                  (close! delays-ch)
                  [res (<! (tst/ch->vec delays-ch))]))))]
    (async done
      (go
        (testing "no retries triggered"
          (is (= (<! (request [:ok])) [:ok []])))
        (testing "no retries triggered for http error when no match"
          (is (= (ex-message (first (<! (request [(err 500 "ooh")])))) "HTTP 500: ooh")))
        (testing "one retry on http 500"
          (is (= (<! (request [(err 500 "foo") :ok])) [:ok [100]])))
        (testing "two retries on http 502"
          (is (= (<! (request [(err 502) (err 502) :ok])) [:ok [100 200]])))
        (testing "two retries exhausted"
          (let [[err delays] (<! (request (repeat 3 (err 502))))
                [msg {:keys [nr-attempts last-result]}] (tst/ex?->vec err)]
            (is (= delays [100 200]))
            (is (= msg "retry limit reached"))
            (is (= nr-attempts 3))
            (is (= (ex-message last-result) "HTTP 502"))))
        (done)))))
