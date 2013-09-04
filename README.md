This is a self-contained wrapper around [LevelDB](https://code.google.com/p/leveldb/), which provides all the necessary binaries via [leveldbjni](https://github.com/fusesource/leveldbjni).

### basic usage

```clj
[factual/clj-leveldb "0.1.0"]
```

To create or access a database, use `clj-leveldb/create-db`:

```clj
clj-leveldb> (def db (create-db "/tmp/leveldb"))
#'clj-leveldb/db
```

This database object can now be used with `clj-leveldb/get`, `put`, `delete`, and `iterator`.

```clj
clj-leveldb> (put db "a" "b")
nil
clj-leveldb> (get db "a")
#<byte[] [B@5e0a614c>
```

Notice that the value returned is a byte-array.  This is because byte arrays are the native storage format for LevelDB, and we haven't defined custom encoders and decoders.  This can be done in `create-db`:

```clj
clj-leveldb> (def db (create-db "/tmp/leveldb" 
                       {:key-decoder byte-streams/to-string 
                        :val-decoder byte-streams/to-string}))
#'clj-leveldb/db
clj-leveldb> (get db "a")
"b"
```

Notice that we haven't defined `key-encoder` or `val-encoder`; this is because there's a default transformation between strings and byte-arrays, which assumes a utf-8 encoding.  If we wanted to support keywords, or use a different encoding, we'd have to explicitly specify encoders.

Both `put` and `delete` can take multiple values, which will be written in batch:

```clj
clj-leveldb> (put db "a" "b" "c" "d" "e" "f")
nil
clj-leveldb> (delete db "a" "c" "e")
nil
```

We can also get a sequence of all key/value pairs, either in the entire database or within a given range using `iterator`:

```clj
clj-leveldb> (put db "a" "b" "c" "d" "e" "f")
nil
clj-leveldb> (iterator db)
(["a" "b"] ["c" "d"] ["e" "f"])
clj-leveldb> (iterator db "c" nil)
(["c" "d"] ["e" "f"])
clj-leveldb> (iterator db nil "c")
(["a" "b"] ["c" "d"])
```

Syncing writes to disk can be forced via `sync`, and compaction can be forced via `compact`.

Full documentation can be found [here](http://factual.github.io/clj-leveldb/).

### license

Copyright © 2013 Factual, Inc.

Distributed under the Eclipse Public License, the same as Clojure.
