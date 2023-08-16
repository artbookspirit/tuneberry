(ns tuneberry.helpers.http
  (:require [clojure.string :as str]
            [lambdaisland.glogi :as log]
            [tuneberry.helpers.common :refer [error]]))

(defn generate-http-params-map [params]
  {:pre [(or (nil? params) (map? params))]}
  (letfn [(dest [kw]
            (let [ns-str (or (namespace kw) "q")]
              (case ns-str
                "q" :query-params
                "b" :json-params)))]
    (reduce
      (fn [ret [kw v]]
        (let [dst (dest kw)]
          (assoc ret dst (assoc (get ret dst {}) (keyword (name kw)) v))))
      {} params)))

(defn generate-request-map [{:keys [api-url path method token params]}]
  {:pre [(every? string? [api-url token]) (str/starts-with? path "/")
         (contains? #{:get :post :put} method)]}
  (let [req (-> {:method            method
                 :url               (str api-url path)
                 :with-credentials? false
                 :oauth-token       token}
                (merge (generate-http-params-map params)))]
    (log/trace :generate-request-map/generated
               {:req (dissoc req :with-credentials? :oauth-token)})
    req))

(defn valid-response? [http-res]
  (let [{:keys [success status]} http-res]
    (and (boolean? success)
         (number? status))))

(defn wrap-error [http-res]
  {:pre [(valid-response? http-res)]}
  (let [{:keys [success status body]} http-res
        msg (some-> body :error :message)]
    (assert (not success))
    (error (str "HTTP " status (some-> msg ((partial str ": "))))
           {:http-status  status
            :http-message msg
            :cause        http-res})))

(defn match-error [thing]
  (when (instance? ExceptionInfo thing)
    (let [data (ex-data thing)]
      (when (contains? data :http-status)
        ((juxt :http-status :http-message :cause) data)))))
