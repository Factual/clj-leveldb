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
    :val-decoder (comp edn/read-string bs/to-string)
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
  (is (= ::foo (l/get db :a ::foo))))

