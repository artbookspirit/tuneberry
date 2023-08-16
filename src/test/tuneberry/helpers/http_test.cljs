(ns tuneberry.helpers.http-test
  (:require [cljs.test :refer [are deftest is]]
            [tuneberry.helpers.common :refer [deep-merge error]]
            [tuneberry.helpers.http :as http]
            [tuneberry.test.utils :refer [ex?->vec failure]]))

(deftest generate-http-params-map-test
  (are [params expected]
    (= (http/generate-http-params-map params) expected)
    nil {}
    {} {}
    {:uri "foo"} {:query-params {:uri "foo"}}
    {:q/uri "foo"} {:query-params {:uri "foo"}}
    {:b/uri "foo"} {:json-params {:uri "foo"}}
    {:uri "foo" :q "query"} {:query-params {:uri "foo" :q "query"}}
    {:uri "foo" :b/num 10} {:query-params {:uri "foo"} :json-params {:num 10}}
    {:q/uri "foo" :b/num 10 :q "query"} {:query-params {:uri "foo" :q "query"}
                                         :json-params  {:num 10}}))

(deftest generate-request-map-test
  (let [add deep-merge
        req {:api-url "https://dummy.api", :path "/my/path"
             :token   "dummy-token", :method :get}
        rmap {:url    "https://dummy.api/my/path", :with-credentials? false
              :method :get, :oauth-token "dummy-token"}]
    (are [x expected]
      (= (http/generate-request-map x) expected)
      req rmap
      (add req {:method :post}) (add rmap {:method :post})
      (add req {:params {:p1 "val"}}) (add rmap {:query-params {:p1 "val"}})
      (add req {:params {:b/p1 "val"}}) (add rmap {:json-params {:p1 "val"}}))))

(deftest wrap-error-test
  (letfn [(wrapped->vec [http-res] (ex?->vec (http/wrap-error http-res)))]
    (is (= (wrapped->vec (failure 404))
           ["HTTP 404" {:http-status  404
                        :http-message nil
                        :cause        (failure 404)}])
        "wrapping error response map without message")
    (is (= (wrapped->vec (failure 404 "Not found"))
           ["HTTP 404: Not found"
            {:http-status  404
             :http-message "Not found"
             :cause        (failure 404 "Not found")}])
        "wrapping error response map with message")))

(deftest match-error-test
  (are [x expected]
    (= (http/match-error x) expected)
    nil nil
    "foo" nil
    (error "e") nil
    (error "e" {:http-status 503}) [503 nil nil]
    (error "e" {:http-status 503 :http-message "m"}) [503 "m" nil]
    (error "e" {:http-status 503 :http-message "m" :cause "c"}) [503 "m" "c"]))
