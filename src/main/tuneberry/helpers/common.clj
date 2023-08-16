(ns tuneberry.helpers.common)

(defmacro nothrow [& body]
  `(try
     ~@body
     (catch js/Error e# e#)))

(defmacro go-safe [& body]
  `(cljs.core.async/go (nothrow ~@body)))
