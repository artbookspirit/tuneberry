(ns tuneberry.test.auth
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<! go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.string :as str]
            [lambdaisland.glogi :as log]))

(defn code-verifier [n]
  (let [src "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"]
    (apply str (repeatedly n #(rand-nth src)))))

(defn base64-encode [arr-buf]
  (-> (.apply js/String.fromCharCode nil (js/Uint8Array. arr-buf))
      js/btoa
      (str/replace #"\+" "-")
      (str/replace #"/" "_")
      (str/replace #"=+$" "")))

(defn code-challenge [verifier]
  (go
    (let [data (.encode (js/TextEncoder.) verifier)
          digest (<p! (.digest js/window.crypto.subtle "SHA-256" data))]
      (base64-encode digest))))

(defn authorize-url [{:keys [client-id redirect-uri verifier scopes]}]
  {:pre [every? seq [client-id redirect-uri verifier scopes]]}
  (go
    (let [challenge (<! (code-challenge verifier))
          params {:client_id             client-id
                  :response_type         "code"
                  :redirect_uri          redirect-uri
                  :scope                 (str/join " " scopes)
                  :code_challenge_method "S256"
                  :code_challenge        challenge}
          url-params (js/URLSearchParams.)]
      (doseq [[k v] params]
        (.append url-params (name k) v))
      (str "https://accounts.spotify.com/authorize?" (.toString url-params)))))

(defn current-time-sec []
  (/ (.getTime (js/Date.)) 1000))

(defn request-token [form-params]
  (go
    (let [url "https://accounts.spotify.com/api/token"
          issued-at (current-time-sec)
          http-res (<! (http/post url {:form-params form-params}))
          {:keys [expires_in] :as token} (:body http-res)]
      (cond-> token
              expires_in (assoc :expires_at (+ issued-at expires_in))))))

(defn get-access-token [{:keys [client-id redirect-uri verifier code]}]
  {:pre [every? seq [client-id redirect-uri verifier code]]}
  (request-token {:client_id     client-id
                  :grant_type    "authorization_code"
                  :code          code
                  :redirect_uri  redirect-uri
                  :code_verifier verifier}))

(defn refresh-access-token [{:keys [client-id refresh-token]}]
  {:pre [every? seq [client-id refresh-token]]}
  (request-token {:client_id     client-id
                  :grant_type    "refresh_token"
                  :refresh_token refresh-token}))

(defn token-expired? [{:keys [expires_at] :as _access-token}]
  {:pre [(number? expires_at)]}
  (let [curr-time (current-time-sec)
        ret (> curr-time expires_at)]
    (when ret
      (log/debug :token-expired?/access-token-expired
                 {:curr-time curr-time :expires_at expires_at}))
    ret))

(defn make-token-fn [client-id access-token]
  (let [token (atom access-token)]
    (fn []
      (go
        (when (token-expired? @token)
          (reset! token (<! (refresh-access-token
                              {:client-id     client-id
                               :refresh-token (:refresh_token @token)}))))
        (:access_token @token)))))
