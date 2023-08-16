(ns tuneberry.helpers.utils-test
  (:require [cljs.core.async :refer [<! >! chan close! go]]
            [cljs.test :refer [are async deftest is testing]]
            [tuneberry.core :refer [tuneberry]]
            [tuneberry.helpers.common :as hlp :refer [error error?]]
            [tuneberry.helpers.utils :as utils]
            [tuneberry.test.utils :as tst :refer [ch->vec]]))

(deftest redo-test
  (letfn [(run [max-attempts f-results & opts]
            (let [{:keys [pred delays-fn error-msg wait-fn]
                   :or   {delays-fn (hlp/binary-geometric-seq 100)
                          pred      (partial = :ok)}} opts
                  [ch f] (tst/go-from-coll-chan f-results)
                  delays-ch (chan 10)
                  wait-fn (or wait-fn (fn [n] (go (>! delays-ch n))))
                  opts (cond-> {:max-attempts max-attempts
                                :delays-fn    delays-fn
                                :wait-fn      wait-fn}
                               error-msg (assoc :error-msg error-msg))]
              (go
                (let [res (<! (utils/redo f pred opts))]
                  (close! delays-ch)
                  (is (= [] (<! (ch->vec ch))) "all expected f results used")
                  [(tst/ex?->vec res) (<! (ch->vec delays-ch))]))))]
    (async done
      (go
        (testing "success at first attempt"
          (is (= (<! (run 1 [:ok])) [:ok []])))
        (testing "success at second attempt"
          (is (= (<! (run 2 [:meh :ok])) [:ok [100]])))
        (testing "failure after three attempts"
          (is (= (<! (run 3 [:meh :meh :meh]))
                 [["attempts limit reached" {:nr-attempts 3 :last-result :meh}]
                  [100 200]])))
        (testing "success in four attempts"
          (is (= (<! (run 4 [:meh :meh :meh :ok])) [:ok [100 200 400]])))
        (testing "no calls to delay func for zero delays"
          (is (= (<! (run 4 [:meh :meh :meh :ok] :delays-fn (constantly [0 700 0])))
                 [:ok [700]])))
        (testing "custom error message"
          (is (= (<! (run 2 [:meh :meh] :error-msg "Foo"))
                 [["Foo" {:nr-attempts 2 :last-result :meh}] [100]])))
        (testing "treating error from f like a regular value"
          (is (= (<! (run 3 [:meh (error "f err") :ok])) [:ok [100 200]])))
        (testing "returns error from f on permissive predicate"
          (is (= (<! (run 3 [:meh (error "f err")] :pred #(error? %)))
                 [["f err"] [100]])))
        (testing "returns error from delay fn"
          (is (= (<! (run 2 [:meh] :wait-fn (tst/go-const (error "delay err"))))
                 [["delay err"] []])))
        (done)))))

(deftest sub-berry-test
  (let [token-fn (tst/go-const "dummy token")
        sub (fn [req opts] (utils/sub-berry req opts))]
    (is (= (sub {:token-fn token-fn} nil)
           (tuneberry token-fn :retry false))
        "default options with retry disabled for no common opts")
    (is (= (sub {:token-fn token-fn :api-url "foo://bar"} nil)
           (tuneberry token-fn :retry false :api-url "foo://bar"))
        "merges request's common options")
    (is (= (sub {:token-fn token-fn :max-poll 10} nil)
           (tuneberry token-fn :retry false))
        "does not merge request's regular options")
    (is (= (sub {:token-fn token-fn} {:max-poll 10})
           (tuneberry token-fn :retry false :max-poll 10))
        "merges extra options")))

(deftest kwds-enabled?-test
  (are [req kwds expected] (= (utils/kwds-enabled? req kwds) expected)
                           {} [:foo] false
                           {} [:foo :bar] false
                           {:foo true} [:foo] true
                           {:foo false} [:foo] false
                           {:foo nil} [:foo] false
                           {:foo [1 2]} [:foo] true
                           {:foo true} [:bar] false
                           {:foo true} [:foo :bar] false
                           {:foo true :bar false} [:foo :bar] false
                           {:foo false :bar true} [:foo :bar] false
                           {:foo true :bar true} [:foo :bar] true
                           {:foo false :bar false} [:foo :bar] false))

(deftest query-map->str-test
  (are [m expected]
    (= (utils/query-map->str m) expected)
    {} ""
    {:artist "Plaid"} "artist:Plaid"
    {:artist "Aphex Twin"} "artist:Aphex Twin"
    {:artist "Aphex Twin" :album "Syro"} "album:Syro artist:Aphex Twin"))

(deftest criterion-met-test
  (are [status msg criterion expected]
    (= (utils/criterion-met status msg criterion) expected)
    501 nil 501 true
    501 nil 502 false
    501 nil [501 #"bar"] false
    501 nil [502 #"bar"] false
    501 "foobar" 501 true
    501 "foobar" 502 false
    501 "foobar" [501 #"bar"] true
    501 "foobar" [501 #"baz"] false
    501 "foobar" [502 #"bar"] false
    501 "foobar" [502 #"baz"] false))
