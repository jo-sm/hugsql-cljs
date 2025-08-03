(ns hugsql-cljs.core
  (:require [clojure.java.io :as io]))

(defmacro db-fns-from-files
  "Creates a map of db functions for `paths`. Options are any valid options for `map-of-db-fns-from-string`.

  Usage:
  ```clj
  (hugsql/db-fns-from-files {:adapter sqlite-adapter}
                            \"sql/app.sql\"
                            \"sql/internal.sql\")
  ```
  "
  [options & paths]
  (let [sources (map #(slurp (io/resource %)) paths)]
    `(let [maps# (map #(hugsql-cljs.core/map-of-db-fns-from-string % ~options) [~@sources])]
       (apply merge maps#))))