(ns tuneberry.users
  (:require [tuneberry.helpers.framework :refer [request!]]))

(defn get-current-user-profile
  "Returns profile information for the current user. No params.

  https://developer.spotify.com/documentation/web-api/reference/get-current-users-profile"
  [tb & {:as keyvals}]
  (request! tb
            :method :get
            :path "/me"
            :msg :get-current-user-profile/getting
            :mixed keyvals))
