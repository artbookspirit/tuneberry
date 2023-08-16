(ns tuneberry.core)

(defmacro <? [expr]
  `(tuneberry.helpers.common/throw-error (cljs.core.async/<! ~expr)))
