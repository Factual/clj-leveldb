(ns clj-rocksdb-test
  (:require
    [clojure.test :refer :all]
    [clj-rocksdb :as r]
    [byte-streams :as bs]
    [clojure.edn :as edn])
  (:import
    [java.util
     UUID]
    [java.io
     File]))


(def db
  (r/create-db
    (doto (File. (str "/tmp/" (UUID/randomUUID)))
      .deleteOnExit)
    {:key-encoder name
     :key-decoder (comp keyword bs/to-string)
     :val-decoder (comp edn/read-string bs/to-char-sequence)
     :val-encoder pr-str}))

(deftest test-basic-operations
  (r/put db :a :b)
  (is (= :b
        (r/get db :a)
        (r/get db :a ::foo)))
  (is (= [[:a :b]]
        (r/iterator db)
        (r/iterator db :a)
        (r/iterator db :a :a)
        (r/iterator db :a :c)))
  (is (= nil
        (r/iterator db :b)
        (r/iterator db :b :d)))
  (r/delete db :a)
  (is (= nil (r/get db :a)))
  (is (= ::foo (r/get db :a ::foo)))

  (r/put db :a :b :z :y)
  (is (= :b (r/get db :a)))
  (is (= :y (r/get db :z)))

  (is (= [[:a :b] [:z :y]]
        (r/iterator db)))
  (is (= [[:a :b]]
        (r/iterator db :a :x)
        (r/iterator db nil :x)))
  (is (= [[:z :y]]
        (r/iterator db :b)
        (r/iterator db :b :z)))

  (is (= [:a :z] (r/bounds db)))

  (r/compact db)

  (with-open [snapshot (r/snapshot db)]
    (r/delete db :a :z)
    (is (= nil (r/get db :a)))
    (is (= :b (r/get snapshot :a))))

  (r/compact db))
