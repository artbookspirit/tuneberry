(ns tuneberry.core
  (:require-macros [tuneberry.core])
  (:require [cljs.core.async :refer [go]]
            [tuneberry.helpers.common :as hlp]))

(defn tuneberry
  "Creates a new tuneberry object with token source token-src and the specified
  options. The tuneberry object is a configuration object that is passed to every
  function that calls the Spotify API, such as tuneberry.player/get-user-queue.

  If token-src is a function, it is called before each use of the Spotify API,
  and is expected to return a core.async channel containing a valid OAuth 2.0
  access token. If the access token has expired, it should be refreshed before
  returning. The tuneberry library itself deliberately does not deal with token
  management.

  token-src may also be a string containing an OAuth 2.0 access token, which in
  this case will be repeatedly returned without refreshing. This allows for a
  quick and working, but non-production setup.

  The tuneberry options configure the behavior of the library when making Spotify
  API requests, such as the number of retries or postprocessing. See the
  README.md file for a list of supported options."
  [token-src & {:as opts}]
  {:token-fn (if (string? token-src)
               #(go token-src)
               token-src)
   :config   (hlp/deep-merge hlp/default-option-map opts)})

(defn options
  "Returns the options contained in the tuneberry object."
  [tb]
  (:config tb))

(defn token-fn
  "Returns the token function contained in the tuneberry object."
  [tb]
  (:token-fn tb))
