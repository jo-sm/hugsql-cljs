# HugSQL CLJS

A CLJS port of HugSQL. It provides the core of the original library, but changes and removes some functionality for simplicity or preference of the author. 

**Note**: this port is unaffiliated with the original.

# Getting started and basic usage

If you're not familiar with HugSQL, please take a look at [the original docs](https://www.hugsql.org) and keep in mind [the things below](#changes-and-things-to-keep-in-mind).

If you are familiar with HugSQL, treat this library as a cousin of the original: they come from the same lineage but from different families.

```clj
(ns my.app-worker
  (:require [hugsql-cljs.core :as hugsql])) 

                                       ;; For now you'll need to make this adapter yourself.
                                       ;; In the future one may be provided in the `hugsql-adapter` namespace
(def queries (hugsql/db-fns-from-files {:adapter sqlite-adapter}

                                       ;; These files should be an available resource
                                       ;; via `io/resource`
                                       "sql/app.sql"
                                       "sql/internal.sql"))

(let [query (get queries :get-user-by-id)]
  (query db {:id 1234}))
```

# Changes and things to keep in mind

- The essential functionality is here. You can define a SQL file with queries (or use strings) that have the `-- :name` header and it will be defined as expected when run through one of the available functions.
- The query functions remain synchronous. This means that you need to use it with a synchronous DB instance e.g. `sqlite-wasm` in a webworker; you can't use this directly in your app's main thread code.
- The way the adapters work is the same as in the original.

## Breaking API changes

- Only the `:name` header is supported; `:snip`, and private methods (`:name-`, `:snip-`) are not supported. `:snip` is removed for simplicity, and the private methods are removed since I am not sure of the use case and haven't seen it used in practice.
- Defining functions using `def-db-fns` is removed in favor of the `map-of-db-fns-from-string` function and `db-fns-from-files` macro. Since the expected common use case is using this library in a webworker and passing messages from the main thread to query the DB, dealing with functions in a map rather than functions defined in the namespace is more straightforward and makes it easier to debug/introspect.
- Retrieving the sqlvec version of a query is done by calling the query function with `{:only-sqlvec? true}` in the options map (`(query db {:id 1234} {:only-sqlvec? true})`). This simplifies the use case where you need both the prepared SQL and to be able to call the query function.
- Expressions in the SQL are removed and won't be readded. Realistically this would require a self-hosted CLJS compiler, which would add too much bloat, and I also personally find CLJ expressions in SQL difficult to reason about. If you need the kind of expessivity that you get from this functionality I recommend something like HoneySQL.

# Appendix

It goes without saying: many thanks to Layerware for the original HugSQL library! When I've had to use ORMs or other higher level wrappers in other languages I always miss it and wish I could just use SQL, and I'm glad we're able to use an actual DB in the browser with the same lovely library now too.

- [Original HugSQL Docs: https://www.hugsql.org](https://www.hugsql.org)
- [API Docs](https://cljdoc.org/d/com.layerware/hugsql-core/)

Copyright `hugsql` (2b6589f02f6e9052abe2a434bc97b2c5aa532f98 and before) © [Layerware, Inc.](http://www.layerware.com)
Copyright `hugsql-cljs` (after 2b6589f02f6e9052abe2a434bc97b2c5aa532f98) © Joshua Smock

Distributed under the [Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)
