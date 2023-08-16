(ns tuneberry.helpers.mappers
  (:require [lambdaisland.glogi :as log]
            [tuneberry.helpers.common :refer [error]]
            [tuneberry.helpers.http :as http]
            [tuneberry.helpers.utils :refer [criterion-met error-or]]))

(defn map-error [_]
  (fn [res]
    {:pre [(http/valid-response? res)]}
    (if (:success res)
      res
      (let [err (http/wrap-error res)]
        (log/debug :map-error/err-wrapped {:res res :err err})
        err))))

(defn map-suppress [{:keys [suppress] :as _req}]
  {:pre [(coll? suppress) (seq suppress)]}
  (fn [res]
    (or (when-let [[status msg cause] (http/match-error res)]
          (when (some (partial criterion-met status msg) suppress)
            (log/debug :map-suppress/suppressed
                       {:status status :msg msg :cause cause})
            cause))
        res)))

(defn map-smart [_]
  (error-or
    (fn [res]
      (if (contains? res :body)
        (let [body (:body res)]
          (if (empty? body) {} body))
        res))))

(defn map-select [{:keys [sel sel-check] :as _req}]
  (error-or
    (fn [res]
      (let [ret (get-in res sel)]
        (if (and sel-check (nil? ret))
          (error "no response path" {:response res :path sel})
          ret)))))

(defn map-post [{:keys [post post-check] :as _req}]
  (error-or
    (fn [res]
      (let [ret (post res)]
        (if (and post-check (nil? ret))
          (error "post result is nil" {:response res})
          ret)))))
