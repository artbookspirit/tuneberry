(ns tuneberry.search
  (:require [tuneberry.helpers.framework :refer [request!]]
            [tuneberry.helpers.utils :refer [query-map->str]]))

(defn search
  "Searches the Spotify catalog. Params: q, type, market, limit, offset,
  include_external.

  The q parameter can be specified either as a string or as a map. For example,
  the following queries are the same:
  :q \"artist:Aphex Twin album:Syro\"
  :q {:artist \"Aphex Twin\" :album \"Syro\"}

  https://developer.spotify.com/documentation/web-api/reference/search"
  [tb & {:keys [q] :as keyvals}]
  {:pre [(or (string? q) (map? q)) (some? q)]}
  (request! tb
            :method :get
            :path "/search"
            :msg :search/searching
            :mixed (cond-> keyvals (map? q) (update :q query-map->str))))
