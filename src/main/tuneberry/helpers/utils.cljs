(ns tuneberry.helpers.utils
  (:require [cljs.core.async :refer [<! timeout]]
            [clojure.string :as str]
            [lambdaisland.glogi :as log]
            [tuneberry.core :refer [<? options tuneberry]]
            [tuneberry.helpers.common :as hlp :refer [error go-safe]]))

(defn redo
  "Calls f until pred matches its result from the returned channel or
  the max-attempts limit is reached. Returns a channel with the result
  of the last call to f.

  If max-attempts is reached, an ExceptionInfo is returned with message
  error-msg and the last result of f in the data map."
  [f pred & opts]
  (let [{:keys [max-attempts delays-fn error-msg wait-fn]
         :or   {max-attempts 3
                delays-fn    (hlp/binary-geometric-seq 100)
                error-msg    "attempts limit reached"
                wait-fn      timeout}} opts]
    (go-safe
      (loop [attempt-nr 1
             delays (delays-fn)]
        (let [delay-msec (first delays)
              result (<! (f))
              match (pred result)]
          (log/trace :redo/f-called {:result result :attempt-nr attempt-nr
                                     :match  match})
          (if match
            result
            (if (>= attempt-nr max-attempts)
              (error error-msg :nr-attempts attempt-nr :last-result result)
              (do
                (assert (some? delay-msec))
                (when (pos? delay-msec)
                  (log/trace :redo/waiting
                             {:attempt-nr attempt-nr :delay-msec delay-msec})
                  (<? (wait-fn delay-msec)))
                (recur (inc attempt-nr) (rest delays))))))))))

(defn error-or [f]
  (fn [res]
    (if (hlp/error? res)
      res
      (f res))))

(defn sub-berry
  "Return a new isolated configuration for subtransactions (like polling done
  while blocking). From the request copy only the common options, and take
  defaults for the rest. Also, disable the retry functionality (http errors can
  be returned from subtransactions and handled by the retry middleware from the
  main transaction)."
  [req extra-opts]
  {:pre [(map? req) (or (nil? extra-opts) (map? extra-opts))]}
  (let [common-opts (select-keys req hlp/common-option-list)
        opts (hlp/deep-merge hlp/default-option-map
                             common-opts
                             extra-opts
                             {:retry false})]
    (tuneberry (:token-fn req) opts)))

(defn kwds-enabled? [req kwds]
  {:pre [(map? req) (seq kwds) (every? keyword? kwds)]}
  ((apply every-pred kwds) req))

(defn query-map->str [m]
  (let [sm (into (sorted-map) m)
        items (for [[k v] sm] (str (name k) ":" v))]
    (str/join " " items)))

(defn criterion-met
  "A criterion is a number n that has to match http status or a vector [n re]
  where, in addition, re has to match a substring of msg. If msg is empty and
  there is a re specified, function returns false."
  [status msg criterion]
  {:pre [(number? status) (or (nil? msg) (string? msg))
         (or (number? criterion)
             (and (coll? criterion) (= (count criterion) 2)))]}
  (if (number? criterion)
    (= status criterion)
    (let [[code re] criterion]
      (and (= status code)
           (some? msg)
           (boolean (re-find re msg))))))
