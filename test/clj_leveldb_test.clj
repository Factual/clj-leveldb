(ns clj-leveldb-test
  (:require
    [clojure.test :refer :all]
    [clj-leveldb :as l]
    [byte-streams :as bs]
    [clojure.edn :as edn])
  (:import
    [java.util
     UUID]
    [java.io
     File]))


(def db
  (l/create-db
    (doto (File. (str "/tmp/" (UUID/randomUUID)))
      .deleteOnExit)
    :key-encoder name
    :key-decoder (comp keyword bs/to-string)
    :val-decoder (comp edn/read-string bs/to-char-sequence)
    :val-encoder pr-str))

(deftest test-basic-operations
  (l/put db :a :b)
  (is (= :b
        (l/get db :a)
        (l/get db :a ::foo)))
  (is (= [[:a :b]]
        (l/iterator db)
        (l/iterator db :a)
        (l/iterator db :a :a)
        (l/iterator db :a :c)))
  (is (= nil
        (l/iterator db :b)
        (l/iterator db :b :d)))
  (l/delete db :a)
  (is (= nil (l/get db :a)))
  (is (= ::foo (l/get db :a ::foo)))

  (l/put db :a :b :z :y)
  (is (= :b (l/get db :a)))
  (is (= :y (l/get db :z)))

  (is (= [[:a :b] [:z :y]]
        (l/iterator db)))
  (is (= [[:a :b]]
        (l/iterator db :a :x)
        (l/iterator db nil :x)))
  (is (= [[:z :y]]
        (l/iterator db :b)
        (l/iterator db :b :z)))

  (is (= [:a :z] (l/bounds db)))

  (with-open [snapshot (l/snapshot db)]
    (l/delete db :a :z)
    (is (= nil (l/get db :a)))
    (is (= :b (l/get snapshot :a)))))

