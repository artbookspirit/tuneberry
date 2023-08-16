(ns tuneberry.test.runner
  {:dev/always true}
  (:require [cljs-test-display.core :as ctd]
            [cljs.core.async :refer [<! go]]
            [cljs.pprint :refer [pprint]]
            [lambdaisland.glogi :as log]
            [lambdaisland.glogi.console :as glogi-console]
            [shadow.dom :as dom]
            [shadow.test :as st]
            [shadow.test.env :as env]
            [tuneberry.test.auth :as auth]
            [tuneberry.test.utils :as tst]))

(defn start []
  (-> (env/get-test-data)
      (env/reset-test-data!))
  (st/run-all-tests (ctd/init! "test-root")))

(defn stop [done]
  ; tests can be async. You must call done so that the runner knows you actually
  ; finished
  (done))

(def test-url "http://127.0.0.1:8021")
(def redirect-uri (str test-url "/callback"))
(def scopes ["user-read-private"
             "user-read-email"
             "user-read-playback-state"
             "user-modify-playback-state"
             "user-read-recently-played"])

(defn store-client-id-from-params [params]
  (let [client-id (.get params "client-id")]
    (when (seq client-id)
      (.setItem js/localStorage "client-id" client-id)
      (log/config :get-client-id!/stored {:client-id client-id}))))

(defn ^:export init []
  (glogi-console/install!)
  ;; https://github.com/lambdaisland/glogi
  ;; debug > trace > finest
  (log/set-levels {:glogi/root :info
                   'tuneberry  :debug})
  (let [params (js/URLSearchParams. js/window.location.search)
        _ (store-client-id-from-params params)
        client-id (.getItem js/localStorage "client-id")
        code (.get params "code")]
    (if (empty? client-id)
      (js/alert
        (str
          "Initialize the tests by specifying your Spotify application's "
          "Client ID in the url query string. It should be in the form:\n\n"
          test-url "?client-id=<Client ID>\n\n"
          "This only needs to be done once."))
      (if (nil? code)
        ;; Initial state: not authorized. Navigate to the Spotify authorization
        ;; url and redirect back to the test url. This will make init run again
        ;; with the authorization code passed in the url query string.
        (let [verifier (auth/code-verifier 128)]
          (.setItem js/localStorage "verifier" verifier)
          (go
            (let [auth-url (<! (auth/authorize-url
                                 {:client-id    client-id
                                  :redirect-uri redirect-uri
                                  :verifier     verifier
                                  :scopes       scopes}))]
              (set! (.-location js/document) auth-url))))
        ;; Exchange code for access token and run tests
        (let [verifier (.getItem js/localStorage "verifier")]
          (go
            (let [token (<! (auth/get-access-token
                              {:client-id    client-id
                               :redirect-uri redirect-uri
                               :verifier     verifier
                               :code         code}))]
              (if (not (some? (:access_token token)))
                (js/alert (str "Failed to retrieve token:" token))
                (do
                  ;; "Remove" the query string (containing the code) from the
                  ;; browser's URL bar, so that manually refreshing the page will
                  ;; take us back to the "not authorized" state. (The code is
                  ;; already used up.)
                  (.replaceState js/window.history nil "" test-url)
                  (.log js/console (str "Received access token:\n"
                                        (with-out-str (pprint token))))
                  (tst/init! client-id token)
                  (dom/append [:div#test-root])
                  (start))))))))))
