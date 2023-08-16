(ns tuneberry.helpers.middleware
  (:require [cljs.core.async :refer [promise-chan put! take!]]
            [lambdaisland.glogi :as log]
            [tuneberry.core :refer [<?]]
            [tuneberry.helpers.common :refer [error? go-safe]]
            [tuneberry.helpers.http :as http]
            [tuneberry.helpers.utils :refer [criterion-met sub-berry redo]]))

(defn with-mappers [& mappers]
  (fn [handler]
    (fn [req]
      (let [mapping-fns (map #(% req) mappers)
            result-fn (apply comp (reverse mapping-fns))
            ch (promise-chan)]
        (take! (handler req) #(put! ch (result-fn %)))
        ch))))

(defn with-token [handler]
  (fn [{:keys [token-fn] :as req}]
    (let [ch (promise-chan)
          token-ch (token-fn)
          f (fn [token] (if (error? token)
                          (put! ch token)
                          (take! (handler (assoc req :token token))
                                 #(put! ch %))))]
      (take! token-ch f)
      ch)))

(defn with-blocking [handler]
  (fn [{:keys [block-until max-poll poll-delays-fn wait-fn] :as req}]
    (let [{:keys [state-fn pred opts initial-poll?]} block-until]
      (go-safe
        (let [sb (sub-berry req opts)
              poll-fn (partial state-fn sb)
              initial-state (when initial-poll? (<? (poll-fn)))
              _ (when initial-poll? (log/trace
                                      :with-blocking/initial-state-obtained
                                      {:initial-state initial-state}))
              ret (<? (handler req))
              ;; forward errors from state-fn right away: they may be a subject
              ;; to retries, but this blocking is already over
              done? #(or (error? %)
                         (pred (if initial-poll? [initial-state %] %)))]
          (<? (redo poll-fn done? {:max-attempts max-poll
                                   :delays-fn    poll-delays-fn
                                   :error-msg    "polling limit reached"
                                   :wait-fn      wait-fn}))
          ret)))))

(defn with-retry [handler]
  (fn [{:keys [retry max-retry retry-delays-fn wait-fn] :as req}]
    {:pre [(coll? retry) (seq retry)]}
    (let [retry? (fn [res]
                   (when-let [[status msg _] (http/match-error res)]
                     (some (partial criterion-met status msg) retry)))]
      (redo (partial handler req)
            (complement retry?)
            {:max-attempts (inc max-retry)
             :delays-fn    retry-delays-fn
             :error-msg    "retry limit reached"
             :wait-fn      wait-fn}))))
