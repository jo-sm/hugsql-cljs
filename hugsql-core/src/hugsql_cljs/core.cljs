(ns hugsql-cljs.core
  (:require [hugsql-cljs.parser :as parser]
            [hugsql-cljs.parameters :as parameters]
            [hugsql-cljs.adapter :as adapter]
            [clojure.string :as string]
            [cljs.tools.reader.edn :as edn])
  (:require-macros hugsql-cljs.core))

(defn ^:no-doc parsed-defs-from-string
  "Given a hugsql `sql` string,
   parse it, and return the defs."
  [sql]
  (parser/parse sql))

(defn ^:no-doc validate-parsed-def!
  "Ensure SQL required headers are provided
   and throw an exception if not."
  [pdef]
  (let [hdr (:hdr pdef)]
    (when-not (:name hdr)
      (throw (ex-info
              (str "Missing HugSQL header :name\n"
                   "Found headers include: " (pr-str (vec (keys hdr))) "\n"
                   "SQL: " (pr-str (:sql pdef))) {})))
    (when (every? empty? [(:name hdr) (:name- hdr)])
      (throw (ex-info
              (str "HugSQL Header :name not given.\n"
                   "SQL: " (pr-str (:sql pdef))) {})))))

(defn ^:no-doc validate-parameters!
  "Ensure `sql-template` parameters match provided `param-data`,
   and throw an exception if mismatch."
  [sql-template param-data]
  (let [not-found ::not-found]
    (doseq [k (map :name (filter map? sql-template))]
      (when-not
       (not-any?
        #(= not-found %)
        (map #(get-in param-data % not-found)
             (rest (reductions
                    (fn [r x] (conj r x))
                    []
                    (parameters/deep-get-vec k)))))
        (throw (ex-info
                (str "Parameter Mismatch: "
                     k " parameter data not found.") {}))))))

(defn ^:no-doc prepare-sql
  "Takes a `sql-template` (from hugsql parser) and the runtime-provided
  `param-data` and creates a sqlvec e.g. `[\"sql\" val1 val2]` 
  suitable for adapter execution."
  ([sql-template param-data options]
   (validate-parameters! sql-template param-data)
   (let [applied (map
                  #(if (string? %)
                     [%]
                     (parameters/apply-hugsql-param % param-data options))
                  sql-template)
         sql    (string/trim (string/join "" (map first applied)))
         params (apply concat (filterv seq (map rest applied)))]
     (apply vector (string/trim sql) params))))

(def default-db-options {:quoting :off})

(defn- str->key
  [str]
  (keyword (string/replace-first str #":" "")))

(defn ^:no-doc command-sym
  [hdr]
  (let [nam (:name hdr)]
    (or
     ;;                ↓ short-hand command position
     ;; -- :name my-fn :? :1
     (when-let [c (second nam)] (str->key c))
     ;; -- :command :?
     (when-let [c (first (:command hdr))] (str->key c))
     ;; default
     :query)))

(defn ^:no-doc result-sym
  [hdr]
  (let [nam (:name hdr)]
    (keyword
     (or
      ;;                   ↓ short-hand result position
      ;; -- :name my-fn :? :1
      (when-let [r (second (next nam))] (str->key r))
      ;; -- :result :1
      (when-let [r (first (:result hdr))] (str->key r))
      ;; default
      :raw))))

(defmulti hugsql-command-fn identity)
(defmethod hugsql-command-fn :! [_] hugsql-cljs.adapter/execute)
(defmethod hugsql-command-fn :execute [_] hugsql-cljs.adapter/execute)
(defmethod hugsql-command-fn :i! [_] hugsql-cljs.adapter/execute)
(defmethod hugsql-command-fn :insert [_] hugsql-cljs.adapter/execute)
(defmethod hugsql-command-fn :<! [_] hugsql-cljs.adapter/query)
(defmethod hugsql-command-fn :returning-execute [_] hugsql-cljs.adapter/query)
(defmethod hugsql-command-fn :? [_] hugsql-cljs.adapter/query)
(defmethod hugsql-command-fn :query [_] hugsql-cljs.adapter/query)
(defmethod hugsql-command-fn :default [_] hugsql-cljs.adapter/query)

(defmulti hugsql-result-fn identity)
(defmethod hugsql-result-fn :1 [_] hugsql-cljs.adapter/result-one)
(defmethod hugsql-result-fn :one [_] hugsql-cljs.adapter/result-one)
(defmethod hugsql-result-fn :* [_] hugsql-cljs.adapter/result-many)
(defmethod hugsql-result-fn :many [_] hugsql-cljs.adapter/result-many)
(defmethod hugsql-result-fn :n [_] hugsql-cljs.adapter/result-affected)
(defmethod hugsql-result-fn :affected [_] hugsql-cljs.adapter/result-affected)
(defmethod hugsql-result-fn :raw [_] hugsql-cljs.adapter/result-raw)
(defmethod hugsql-result-fn :default [_] hugsql-cljs.adapter/result-raw)

(defn db-fn*
  "Given parsed sql `psql` and optionally a `command`, `result`, and `options`,
  return an anonymous function that can run hugsql database
  execute/queries and supports hugsql parameter replacement"
  ([psql] (db-fn* psql :default :default {}))
  ([psql command] (db-fn* psql command :default {}))
  ([psql command result] (db-fn* psql command result {}))
  ([psql command result options]
   (fn y
     ([db] (y db {} {}))
     ([db param-data] (y db param-data {}))
     ([db param-data opts & command-opts]
      (let [default-opts (merge default-db-options options opts
                                {:command command :result result})
            all-opts (if (seq command-opts)
                       (assoc default-opts :command-options command-opts) default-opts)
            adapter (:adapter all-opts)
            only-sqlvec? (:only-sqlvec? all-opts)
            prepared-sql (prepare-sql psql param-data all-opts)]

        (if only-sqlvec?
          prepared-sql
          (try
            (as-> psql x
              (prepare-sql x param-data all-opts)
              ((hugsql-command-fn command) adapter db x all-opts)
              ((hugsql-result-fn result) adapter x all-opts))
            (catch :default e
              (adapter/on-exception adapter e)))))))))

(defn db-fn
  "Given an sql string and optionally a command, result, and options,
  return an anonymous function that can run hugsql database
  execute/queries and supports hugsql parameter replacement"
  ([sql] (db-fn sql :default :default {}))
  ([sql command] (db-fn sql command :default {}))
  ([sql command result] (db-fn sql command result {}))
  ([sql command result options]
   (let [psql (:sql (first (parser/parse sql {:no-header true})))]
     (db-fn* psql command result options))))

(defn db-fn-map
  "Hashmap of db fn from a parsed def with the form:

   ```
   {:fn-name {:meta {:doc \"doc string\"}
              :fn <anon-db-fn>}
   ```
   "
  [{:keys [sql hdr]} options]
  (let [pnm (:name- hdr)
        nam (symbol (first (or (:name hdr) pnm)))
        doc (or (first (:doc hdr)) "")
        cmd (command-sym hdr)
        res (result-sym hdr)
        mta (if-let [m (:meta hdr)]
              (edn/read-string (string/join " " m)) {})
        met (merge mta
                   {:doc doc
                    :command cmd
                    :result res
                    :file (:file hdr)
                    :line (:line hdr)
                    :arglists '([db]
                                [db params]
                                [db params options & command-options])}
                   (when pnm {:private true}))
        f (db-fn* sql cmd res (assoc options :fn-name nam))]
    {(keyword nam) (with-meta f met)}))

(defn map-of-db-fns-from-string
  "Given a HugSQL SQL string `s`, return a hashmap of database
   functions of the form:

   ```
   {:fn1-name <fn1>
    :fn2-name <fn2>}
   ```

   Usage:

   `(map-of-db-fns-from-string s options?)`

   where:
    - `s` is a string of HugSQL-flavored sql statements
    - `options` (optional) hashmap:
      `{:quoting :off (default) | :ansi | :mysql | :mssql
       :adapter adapter}`

   See [[hugsql.core/def-db-fns]] for `:quoting` and `:adapter` details."
  ([s] (map-of-db-fns-from-string s {}))
  ([s options]
   (let [pdefs (parsed-defs-from-string s)]
     (doseq [pdef pdefs]
       (validate-parsed-def! pdef))
     (apply merge
            (map
             #(db-fn-map % options)
             pdefs)))))

(defn db-run
  "Given a database spec/connection `db`, `sql` string,
   `param-data`, and optional `command`, `result`,
   and `options`, run the `sql` statement"
  ([db sql] (db-run db sql :default :default {} {}))
  ([db sql param-data] (db-run db sql param-data :default :default {}))
  ([db sql param-data command] (db-run db sql param-data command :default {}))
  ([db sql param-data command result] (db-run db sql param-data command result {}))
  ([db sql param-data command result options & command-options]
   (let [f (db-fn sql command result options)]
     (f db param-data command-options))))
