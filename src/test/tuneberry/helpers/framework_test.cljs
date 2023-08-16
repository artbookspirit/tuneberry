(ns tuneberry.helpers.framework-test
  (:require [cljs.core.async :refer [<! go timeout]]
            [cljs.test :refer [are async deftest is testing]]
            [clojure.string :as str]
            [tuneberry.core :refer [<?]]
            [tuneberry.helpers.framework :as fw]
            [tuneberry.test.utils :as tst :refer [err failure go-const success]]))

(deftest added-by-test
  (let [mul-mapper (fn [req] (fn [res] (update res :n (partial * (:mul req)))))
        wrapped (fw/added-by [:mul] mul-mapper)]
    (are [req res expected] (= ((wrapped req) res) expected)
                            {} {:n 42} {:n 42}
                            {:mul 3} {:n 42} {:n 126})))

(deftest enabled-by-test
  (let [inc-handler (fn [req] (update req :n inc))
        x10-midware (fn [hdl] (fn [req] (update (hdl req) :n (partial * 10))))
        wrapped ((fw/enabled-by [:x10] x10-midware) inc-handler)]
    (are [req expected] (= (wrapped req) expected)
                        {:n 1} {:n 2}
                        {:n 1 :x10 true} {:n 20 :x10 true})))

(deftest wrap-handler-test
  (let [request (fn [req han] ((fw/wrap-handler han) req))
        good-token-fn (go-const "dummy-token")
        bad-token-fn (go-const (err 503 "token err"))
        never-call-han #(assert false "should never be called")]
    (async done
      (go
        (testing "minimal data flow with token"
          (let [req {:token-fn good-token-fn}
                han #(go (if (:token %) (success 200) (failure 401)))]
            (is (= (<! (request req han)) (success 200)))))
        (testing "request short-circuiting when token error (no retry)"
          (let [req {:token-fn bad-token-fn}
                han never-call-han]
            (is (= (ex-message (<! (request req han))) "HTTP 503: token err"))))
        (testing "returns error http response map when suppressing"
          (let [req {:token-fn good-token-fn :suppress [503]}
                han (go-const (failure 503 "unavailable"))]
            (is (= (<! (request req han)) (failure 503 "unavailable")))))
        (let [retry-cfg {:retry           [503]
                         :max-retry       3
                         :retry-delays-fn (constantly (repeat 100))
                         :wait-fn         timeout}]
          (testing "retrying request http error"
            (let [req (merge retry-cfg {:token-fn good-token-fn})
                  han (go-const (failure 503 "unavailable"))
                  error (<! (request req han))]
              (is (= (ex-message error) "retry limit reached"))
              (is (= (-> error ex-data :last-result ex-message)
                     "HTTP 503: unavailable"))))
          (testing "retrying token http error"
            (let [req (merge retry-cfg {:token-fn bad-token-fn})
                  han never-call-han
                  error (<! (request req han))]
              (is (= (ex-message error) "retry limit reached"))
              (is (= (-> error ex-data :last-result ex-message)
                     "HTTP 503: token err"))))
          (testing "retrying http error from polling"
            (let [block-cfg {:blocking       true
                             :max-poll       5
                             :poll-delays-fn (constantly (repeat 100))
                             :block-until
                             {:state-fn (go-const (err 503 "state err"))}}
                  req (merge block-cfg retry-cfg {:token-fn good-token-fn})
                  han (go-const (success 200))
                  error (<! (request req han))]
              (is (= (ex-message error) "retry limit reached"))
              (is (= (-> error ex-data :last-result ex-message)
                     "HTTP 503: state err")))))
        (done)))))

(deftest extract-opts-test
  (are [m expected-opts expected-params]
    (= (fw/extract-opts m) [expected-opts expected-params])
    nil {} {}
    {} {} {}
    {:n 42} {} {:n 42}
    {:b/n 42} {} {:b/n 42}
    {:o/n 42} {:n 42} {}
    {:x 1 :b/y 2 :o/z 3} {:z 3} {:x 1 :b/y 2}))

(deftest request!-test
  (async done
    (go
      (testing "getting available genre seeds"
        (let [res (<? (fw/request!
                        @tst/tb
                        :method :get
                        :path "/recommendations/available-genre-seeds"))]
          (is (some #{"synth-pop"} (:genres res)))))
      (testing "getting catalog information about two artists"
        (let [ids (str/join "," [tst/Katie-Melua tst/Eva-Cassidy])
              artists (<? (fw/request!
                            @tst/tb
                            :method :get
                            :path "/artists"
                            :mixed {:ids    ids
                                    :o/post #(->> % :artists (map :name))}))]
          (is (= artists ["Katie Melua" "Eva Cassidy"]))))
      (testing "getting 404 trying to access invalid endpoint"
        (let [[msg data] (tst/ex?->vec (<! (fw/request!
                                             @tst/tb
                                             :method :get
                                             :path "/meh")))]
          (is (= msg "HTTP 404: Service not found"))
          (is (= (:http-status data) 404))
          (is (= (:http-message data) "Service not found"))
          (is (= (get-in data [:cause :success]) false))))
      (done))))
