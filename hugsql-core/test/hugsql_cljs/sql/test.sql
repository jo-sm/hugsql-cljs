-- :name no-params-select
-- :doc No params
select * from test

-- :name one-value-param
-- :doc One value param
select * from test where id = :id

-- :name multi-value-params
-- :doc Multi value params
select * from test
where id = :id
and name = :name

-- :name value-list-param
-- :doc Value List Param
select * from test
where id in (:v*:ids)

-- :name tuple-param
-- :doc Tuple Param
select * from test
where (id, name) = :tuple:id-name

-- :name tuple-param-list
-- :doc Tuple Param List
insert into test (id, name)
values :t*:people

-- :name identifier-param
-- :doc Identifier param
select * from :i:table-name

-- :name identifier-param-list
-- :doc Identifier param list
select :i*:columns from test

-- :name sql-param
-- :doc Raw SQL param
select * from test order by id :sql:id-order


-- :name create-test-table
-- :command :execute
-- :result :affected
-- :doc Create test table
create table test (
  id    integer primary key,
  name  varchar(20)
)

-- :name create-test-table-mysql :! :n
-- :doc Create test table
create table test (
  id    integer auto_increment primary key,
  name  varchar(20)
)

-- :name create-test-table-h2 :! :n
-- :doc Create test table
create table test (
  id    integer auto_increment primary key,
  name  varchar(20)
)

-- :name create-test-table-hsqldb :! :n
-- :doc Create test table
create table test (
  id    integer identity primary key,
  name  varchar(20)
)

-- :name insert-into-test-table :! :n
-- :doc insert with a regular execute
insert into test (id, name) values (:id, :name)

-- :name insert-into-test-table-returning :<!
-- :doc insert with an sql returning clause
-- only some db's support this
insert into test (id, name) values (:id, :name) returning id

-- :name insert-into-test-table-return-keys :insert :raw
-- behavior of this adapter-specific and db-specific
insert into test (id, name) values (:id, :name)

-- :name insert-multi-into-test-table :! :n
insert into test (id, name) values :tuple*:values

-- :name update-test-table :! :n
update test set name = :name where id = :id

-- :name update-test-table-returning :<! :1
update test set name = :name where id = :id returning id

-- :name update-test-table-returning-private :<! :1
update test set name = :name where id = :id returning id

-- :name select-one-test-by-id :? :1
select * from test where id = :id

-- :name select-ordered :?
select :i*:cols from test
order by :i*:sort-by

-- :name select-deep-get :? :1
select * from test
where id = :records.0.id

-- :name select-namespaced-keyword :? :1
select * from test
where id = :test/id

-- :name select-namespaced-keyword-deep-get :? :1
select * from test
where id = :test.x/records.0.id

-- :name drop-test-table :! :n
-- :doc Drop test table
drop table test

-- :name select-all :? :*
select * from test

-- :name user-meta
/* :meta {:one 1
          :two 2
          :three 3} */
select * from test

