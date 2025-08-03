(ns hugsql-cljs.parameters
  (:require [clojure.string :as string]))

(defn identifier-param-quote
  "Quote the identifier value based on options."
  [value {:keys [quoting no-dot-split] :as options}]
  (let [parts (if no-dot-split [value] (string/split value #"\."))
        qtfn  (condp = quoting
                :ansi #(str \" (string/replace % "\"" "\"\"") \")
                :mysql #(str \` (string/replace % "`" "``") \`)
                :mssql #(str \[ (string/replace % "]" "]]") \])
                ;; off:
                identity)]
    (string/join "." (map qtfn parts))))

(defn deep-get-vec
  "Takes a parameter name and returns a vector
   suitable for get-in lookups where the name starts
   with the form:
     `:employees.0.id`
   Names must be keyword keys in hashmaps in
   param data.
   Numbers must be vector indexes in vectors
   in param data."
  [nam]
  (let [kwfn (fn [x] (if (re-find #"^\d+$" x) (js/parseInt x 10) (keyword x)))
        nmsp (namespace nam)
        nams (string/split (name nam) #"\.")]
    (if nmsp
      (apply vector
             (keyword nmsp (name (kwfn (first nams))))
             (mapv kwfn (rest nams)))
      (mapv kwfn nams))))

(defn value-param [param data _options]
  ["?" (get-in data (deep-get-vec (:name param)))])

(defn value-param-list [param data _options]
  (let [coll (get-in data (deep-get-vec (:name param)))]
    (apply vector
           (string/join "," (repeat (count coll) "?"))
           coll)))

(defn tuple-param [param data options]
  (let [vpl (value-param-list param data options)]
    (apply vector (str "(" (first vpl) ")") (rest vpl))))

(defn tuple-param-list [param data options]
  (let [tuples (map (juxt first rest)
                    (map #(tuple-param {:name :x} {:x %} options)
                         (get-in data (deep-get-vec (:name param)))))
        sql (string/join "," (map first tuples))
        values (apply concat (apply concat (map rest tuples)))]
    (apply vector sql values)))

(defn identifier-param [param data options]
  (let [i (get-in data (deep-get-vec (:name param)))
        param-is-coll? (coll? i)
        i (if param-is-coll? (flatten (into [] i)) i)]
    (if param-is-coll?
      [(str (identifier-param-quote (first i) options)
            " as "
            (identifier-param-quote (second i)
                                    (merge options {:no-dot-split true})))]
      [(identifier-param-quote i options)])))

(defn identifier-param-list [param data options]
  [(string/join
    ", "
    (map
     #(if (vector? %)
        (str (identifier-param-quote (first %) options)
             " as "
             (identifier-param-quote (second %)
                                     (merge options {:no-dot-split true})))
        (identifier-param-quote % options))
     (into [] (get-in data (deep-get-vec (:name param))))))])

(defn sql-param [param data options]
  [(get-in data (deep-get-vec (:name param)))])

(defn sqlvec-param [param data options]
  (get-in data (deep-get-vec (:name param))))

(defn sqlvec-param-list [param data options]
  (reduce
   #(apply vector
           (string/join " " [(first %1) (first %2)])
           (concat (rest %1) (rest %2)))
   (get-in data (deep-get-vec (:name param)))))

(defmulti apply-hugsql-param
  "Implementations of this multimethod apply a hugsql parameter
   for a specified parameter type.  For example:

   ```
   (defmethod apply-hugsql-param :value
     [param data options]
     (value-param param data options)
   ```

   - the `:value` keyword is the parameter type to match on.
   - `param` is the parameter map as parsed from SQL
     (e.g., `{:type :value :name \"id\"}`)
   - `data` is the run-time parameter map data to be applied
     (e.g., `{:id 42}`)
   - `options` contain hugsql options (see [[hugsql.core/def-sqlvec-fns]])

   Implementations must return an sqlvec: a vector containing the resulting
   SQL string in the first position and any values in the remaining positions.
   (e.g., `[\"?\" 42]`)"
  (fn [param _data _options] (:type param)))

(defmethod apply-hugsql-param :v  [param data options] (value-param param data options))
(defmethod apply-hugsql-param :value [param data options] (value-param param data options))
(defmethod apply-hugsql-param :v*  [param data options] (value-param-list param data options))
(defmethod apply-hugsql-param :value* [param data options] (value-param-list param data options))
(defmethod apply-hugsql-param :t [param data options] (tuple-param param data options))
(defmethod apply-hugsql-param :tuple [param data options] (tuple-param param data options))
(defmethod apply-hugsql-param :t* [param data options] (tuple-param-list param data options))
(defmethod apply-hugsql-param :tuple* [param data options] (tuple-param-list param data options))
(defmethod apply-hugsql-param :i [param data options] (identifier-param param data options))
(defmethod apply-hugsql-param :identifier [param data options] (identifier-param param data options))
(defmethod apply-hugsql-param :i* [param data options] (identifier-param-list param data options))
(defmethod apply-hugsql-param :identifier* [param data options] (identifier-param-list param data options))
(defmethod apply-hugsql-param :sql [param data options] (sql-param param data options))
(defmethod apply-hugsql-param :sqlvec [param data options] (sqlvec-param param data options))
(defmethod apply-hugsql-param :sqlvec* [param data options] (sqlvec-param-list param data options))
(defmethod apply-hugsql-param :snip [param data options] (sqlvec-param param data options))
(defmethod apply-hugsql-param :snip* [param data options] (sqlvec-param-list param data options))
