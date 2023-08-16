(ns tuneberry.helpers.mappers-test
  (:require [cljs.test :refer [are deftest is]]
            [tuneberry.helpers.common :refer [error]]
            [tuneberry.helpers.mappers :as mappers]
            [tuneberry.test.utils :refer [err ex?->vec failure success]]))

(deftest map-error-test
  (let [mapper (mappers/map-error {})]
    (is (= (mapper (success 200)) (success 200))
        "response map forwarded on success")
    (is (= (ex-message (mapper (failure 404))) "HTTP 404")
        "ExceptionInfo created on http error map")))

(deftest map-suppress-test
  (letfn [(maps [x]
            ((mappers/map-suppress {:suppress [403 [500 #"f\wo"]]}) x))]
    (is (= (maps (success 200)) (success 200)) "forwards http response map")
    (is (= (ex-message (maps (js/Error "msg"))) "msg") "forwards js error")
    (is (= (ex-message (maps (error "msg"))) "msg") "forwards non-http error")
    (is (= (maps (err 500 "foo")) (failure 500 "foo"))
        "restores http response map from http error when suppressing")
    (is (= (ex-message (maps (err 500))) "HTTP 500")
        "forwards http error when not suppressing")))

(deftest map-smart-test
  (are [x expected] (= (ex?->vec ((mappers/map-smart {}) x)) expected)
                    {} {}
                    {:n 42} {:n 42}
                    {:body {}} {}
                    {:body ""} {}
                    {:body {:n 42}} {:n 42}
                    {:nobody {:n 42}} {:nobody {:n 42}}
                    (js/Error "error msg") ["error msg"]
                    (error "error msg") ["error msg"]))

(def m {:status 200 :success true :body {:foo [1 2 3]}})

(deftest map-select-test
  (are [x path check expected]
    (= (ex?->vec ((mappers/map-select {:sel path :sel-check check}) x))
       expected)
    m [] false m
    m [] true m
    m [:status] false 200
    m [:status] true 200
    m [:body :foo] false [1 2 3]
    m [:body :foo] true [1 2 3]
    m [:body :bar] false nil
    m [:body :bar] true ["no response path" {:path [:body :bar] :response m}]
    (js/Error "msg") [:body :foo] true ["msg"]
    (error "msg") [:body :foo] true ["msg"]))

(deftest map-post-test
  (are [x f check expected]
    (= (ex?->vec ((mappers/map-post {:post f :post-check check}) x)) expected)
    m identity false m
    m identity true m
    m :status false 200
    m :status true 200
    m #(map inc (get-in % [:body :foo])) false [2 3 4]
    m #(map inc (get-in % [:body :foo])) true [2 3 4]
    m #(get-in % [:body :bar]) false nil
    m #(get-in % [:body :bar]) true ["post result is nil" {:response m}]
    (js/Error "msg") #(map inc (get-in % [:body :foo])) true ["msg"]
    (error "msg") #(map inc (get-in % [:body :foo])) true ["msg"]))
