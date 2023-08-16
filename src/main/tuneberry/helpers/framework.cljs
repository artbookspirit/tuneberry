(ns tuneberry.helpers.framework
  (:require [cljs-http.client]
            [lambdaisland.glogi :as log]
            [tuneberry.core :refer [options token-fn]]
            [tuneberry.helpers.common :as hlp]
            [tuneberry.helpers.http :as http]
            [tuneberry.helpers.mappers :as mappers]
            [tuneberry.helpers.middleware :as mid]
            [tuneberry.helpers.utils :refer [kwds-enabled?]]))

(defn added-by
  [kwds mapper]
  (fn [req]
    (if (kwds-enabled? req kwds)
      (mapper req)
      identity)))

(defn enabled-by
  [kwds middleware]
  (fn [handler]
    (fn [req]
      (if (kwds-enabled? req kwds)
        ((middleware handler) req)
        (handler req)))))

(defn wrap-handler [handler]
  (-> handler
      ((mid/with-mappers mappers/map-error
                         (added-by [:suppress] mappers/map-suppress)
                         (added-by [:smart] mappers/map-smart)
                         (added-by [:sel] mappers/map-select)
                         (added-by [:post] mappers/map-post)))
      mid/with-token
      ((enabled-by [:blocking :block-until] mid/with-blocking))
      ((enabled-by [:retry] mid/with-retry))))

(defn extract-opts [keyvals]
  {:pre [(or (nil? keyvals) (map? keyvals))]}
  (reduce
    (fn [[opts params] [kw v]]
      (let [ns-str (namespace kw)]
        (if (= ns-str "o")
          [(assoc opts (keyword (name kw)) v) params]
          [opts (assoc params kw v)])))
    [{} {}] keyvals))

(defn request!
  [tb & {:keys [method path msg opts mixed]}]
  {:pre [(map? tb) (contains? #{:get :post :put} method) (string? path)
         (or (nil? opts) (map? opts)) (or (nil? msg) (keyword? msg))
         (or (nil? mixed) (map? mixed))]}
  (let [http-handler (comp cljs-http.client/request http/generate-request-map)
        handler (wrap-handler http-handler)
        [usr-opts params] (extract-opts mixed)
        request (hlp/deep-merge
                  (options tb)
                  {:method method :path path :token-fn (token-fn tb)
                   :params params}
                  opts
                  usr-opts)]
    (log/debug (or msg :request!/making)
               {:request (dissoc request :api-url :wait-fn :poll-delays-fn
                                 :retry-delays-fn :token-fn)})
    (handler request)))
