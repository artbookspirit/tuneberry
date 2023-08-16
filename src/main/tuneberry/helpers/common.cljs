(ns tuneberry.helpers.common
  (:require-macros [tuneberry.helpers.common])
  (:require [cljs.core.async :refer [timeout]]))

(defn error [msg & {:as data}]
  {:pre [(string? msg) (or (nil? data) (map? data))]}
  (ex-info msg data))

(defn error? [thing]
  (instance? js/Error thing))

(defn throw-error [thing]
  (if (error? thing)
    (throw thing)
    thing))

(defn deep-merge [& maps]
  (letfn [(merge-pair [x1 x2]
            (if (some map? [x1 x2])
              (merge-with merge-pair x1 x2)
              x2))]
    (apply merge-with merge-pair maps)))

(defn binary-geometric-seq [n]
  (fn []
    (iterate (partial * 2) n)))

;;       .o8             .o88o.                       oooo      .
;;      "888             888 `"                       `888    .o8
;;  .oooo888   .ooooo.  o888oo   .oooo.   oooo  oooo   888  .o888oo  .oooo.o
;; d88' `888  d88' `88b  888    `P  )88b  `888  `888   888    888   d88(  "8
;; 888   888  888ooo888  888     .oP"888   888   888   888    888   `"Y88b.
;; 888   888  888    .o  888    d8(  888   888   888   888    888 . o.  )88b
;; `Y8bod88P" `Y8bod8P' o888o   `Y888""8o  `V88V"V8P' o888o   "888" 8""888P'

(def default-option-map
  {:api-url         "https://api.spotify.com/v1"
   :wait-fn         timeout
   ;; --- mappers ---
   :smart           true
   :sel-check       true
   :post-check      true
   ;; --- blocking ---
   :blocking        false
   :max-poll        5
   :poll-delays-fn  (binary-geometric-seq 100)
   ;; --- retry ---
   :retry           [500 502 503]
   :max-retry       3
   :retry-delays-fn (binary-geometric-seq 500)})

(def ^{:doc "Options copied to sub-configs e.g. when polling, see sub-berry."}
  common-option-list [:api-url])
