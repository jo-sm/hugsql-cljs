(ns hugsql-cljs.expr-run
  "HugSQL auto-defines expressions in this namespace")

(def ^:dynamic exprs (atom {}))