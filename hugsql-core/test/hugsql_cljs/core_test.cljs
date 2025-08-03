(ns hugsql-cljs.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [hugsql-cljs.core :as hugsql]
            [hugsql-cljs.adapter]))

(def sql-test-fns (hugsql/db-fns-from-files {:only-sqlvec? true} "hugsql_cljs/sql/test.sql"))

(deftest core
  (testing "defs from string worked"
    (let [fns (hugsql/map-of-db-fns-from-string (str "-- :name test3-select\n select * from test3"
                                                     "-- :name test4-select \n select *"))]
      (is (fn? (:test3-select fns)))
      (is (fn? (:test4-select fns)))))

  (testing "fn definition"
    (let [f (:no-params-select sql-test-fns)]
      (is (fn? f))
      (is (= "No params" (:doc (meta f))))
      (is (= 1 (:line (meta f))))
      (is (= '([db]
               [db params]
               [db params options & command-options])
             (:arglists (meta f))))))

  (testing "sql fns"
    (is (= ["select * from test"] ((:no-params-select sql-test-fns))))
    (is (= ["select * from test"] ((:no-params-select sql-test-fns) nil {})))
    (is (= ["select * from test where id = ?" 1]
           ((:one-value-param sql-test-fns) nil {:id 1})))
    (is (= ["select * from test\nwhere id = ?\nand name = ?" 1 "Ed"]
           ((:multi-value-params sql-test-fns) nil {:id 1 :name "Ed"})))
    (is (= ["select * from test\nwhere id in (?,?,?)" 1 2 3]
           ((:value-list-param sql-test-fns) nil {:ids [1,2,3]})))
    (is (= ["select * from test\nwhere (id, name) = (?,?)" 1 "A"]
           ((:tuple-param sql-test-fns) nil {:id-name [1 "A"]})))
    (is (= ["insert into test (id, name)\nvalues (?,?),(?,?),(?,?)" 1 "Ed" 2 "Al" 3 "Bo"]
           ((:tuple-param-list sql-test-fns) nil {:people [[1 "Ed"] [2 "Al"] [3 "Bo"]]})))
    (is (= ["select * from test"]
           ((:identifier-param sql-test-fns) nil {:table-name "test"})))
    (is (= ["select id, name from test"]
           ((:identifier-param-list sql-test-fns) nil {:columns ["id", "name"]})))
    (is (= ["select * from test as my_test"]
           ((:identifier-param sql-test-fns) nil {:table-name ["test" "my_test"]})))
    (is (= ["select id as my_id, name as my_name from test"]
           ((:identifier-param-list sql-test-fns) nil {:columns [["id" "my_id"], ["name" "my_name"]]})))
    (is (= ["select * from test as my_test"]
           ((:identifier-param sql-test-fns) nil {:table-name {"test" "my_test"}})))
    (is (let [r ((:identifier-param-list sql-test-fns) nil {:columns {"id" "my_id" "name" "my_name"}})]
          (or (= r ["select id as my_id, name as my_name from test"])
              (= r ["select name as my_name, id as my_id from test"]))))
    (is (= ["select * from test order by id desc"]
           ((:sql-param sql-test-fns) nil {:id-order "desc"})))
    (is (= ["select * from test\nwhere id = ?" 42]
           ((:select-namespaced-keyword sql-test-fns) nil {:test/id 42}))))

  (testing "identifier quoting"
    (let [f (:identifier-param sql-test-fns)]
      (is (= ["select * from \"schema\".\"te\"\"st\""]
             (f nil
                {:table-name "schema.te\"st"}
                {:quoting :ansi})))
      (is (= ["select * from \"schema\".\"te\"\"st\" as \"my.test\""]
             (f nil
                {:table-name ["schema.te\"st" "my.test"]}
                {:quoting :ansi})))
      (is (= ["select * from `schema`.`te``st`"]
             (f nil
                {:table-name "schema.te`st"}
                {:quoting :mysql})))
      (is (= ["select * from [schema].[te]]st]"]
             (f nil
                {:table-name "schema.te]st"}
                {:quoting :mssql}))))

    (let [f (:identifier-param-list sql-test-fns)]
      (is (= ["select \"test\".\"id\", \"test\".\"name\" from test"]
             (f nil
                {:columns ["test.id", "test.name"]}
                {:quoting :ansi})))
      (is (= ["select \"test\".\"id\" as \"my.id\", \"test\".\"name\" as \"my.name\" from test"]
             (f nil
                {:columns [["test.id" "my.id"], ["test.name" "my.name"]]}
                {:quoting :ansi})))
      (is (= ["select `test`.`id`, `test`.`name` from test"]
             (f nil
                {:columns ["test.id", "test.name"]}
                {:quoting :mysql})))
      (is (= ["select [test].[id], [test].[name] from test"]
             (f nil
                {:columns ["test.id", "test.name"]}
                {:quoting :mssql})))))

  (testing "`only-sqlvec?` as option to fn"
    (let [f (hugsql/db-fn "select * from test where id = :id")]
      (is (= ["select * from test where id = ?" 1]
             (f nil {:id 1} {:only-sqlvec? true})))))

  (testing "metadata"
    (let [f (:user-meta sql-test-fns)]
      (is (= 1 (:one (meta f))))
      (is (= 2 (:two (meta f))))))

  (testing "command & result as metadata"
    (let [{:keys [select-one-test-by-id select-all]} sql-test-fns]
      (is (= :? (:command (meta select-one-test-by-id))))
      (is (= :1 (:result (meta select-one-test-by-id))))
      (is (= :? (:command (meta select-all))))
      (is (= :* (:result (meta select-all))))))

  (testing "missing header :name"
    (is (thrown-with-msg? js/Error
                          #"Missing HugSQL header :name"
                          (hugsql/map-of-db-fns-from-string "-- :name: almost-a-yesql-name-hdr\nselect * from test"))))

  (testing "nil header :name"
    (is (thrown-with-msg? js/Error
                          #"HugSQL Header :name not given."
                          (hugsql/map-of-db-fns-from-string
                           "-- :name \nselect * from test"))))

  (testing "value parameters allow vectors for ISQLParameter/etc overrides"
    (let [f (hugsql/db-fn "insert into test (id, myarr) values (:id, :v:myarr)")]
      (is (= ["insert into test (id, myarr) values (?, ?)" 1 [1 2 3]]
             (f nil
                {:id 1 :myarr [1 2 3]}
                {:only-sqlvec? true}))))

    (let [f (hugsql/db-fn "insert into test (id, myarr) values :t*:records")]
      (is (= ["insert into test (id, myarr) values (?,?),(?,?)" 1 [1 2 3] 2 [4 5 6]]
             (f nil
                {:records [[1 [1 2 3]]
                           [2 [4 5 6]]]}
                {:only-sqlvec? true})))))

  (testing "spacing around Raw SQL parameters"
    (let [f (hugsql/db-fn "select col_:sql:col_num from test")]
      (is (= ["select col_1 from test"]
             (f nil
                {:col_num 1}
                {:only-sqlvec? true}))))))
