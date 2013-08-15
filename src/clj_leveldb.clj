;; Copyright 2008-2013 Factual Inc.
;; All Rights Reserved.
;;
;; This is UNPUBLISHED PROPRIETARY SOURCE CODE of Factual Inc.
;; Factual Inc. reserves all rights in the source code as
;; delivered. The source code and all contents of this file, may not be
;; used, copied, modified, distributed, sold, disclosed to third parties
;; or duplicated in any form, in whole or in part, for any purpose,
;; without the prior written permission of Factual Inc.

(ns clj-leveldb
  (:refer-clojure :exclude [get])
  (:require
    [clojure.java.io :as io]
    [byte-streams :as bs])
  (:import
    [java.io
     Closeable]
    [org.fusesource.leveldbjni
     JniDBFactory]
    [org.iq80.leveldb
     DBIterator
     Options
     ReadOptions
     DB]))

;;;

(defrecord Snapshot [^DB db ^ReadOptions read-options]
  Closeable
  (close [_] (-> read-options .snapshot .close)))

(defn- closeable-seq
  "Creates a seq which can be closed, given a latch which can be closed
   and dereferenced to check whether it's already been closed."
  [s close-fn]
  (if (empty? s)

    ;; if we've exhausted the seq, just close it
    (do
      (close-fn)
      nil)

    (reify

      Closeable
      (close [this]
        (close-fn))

      clojure.lang.Sequential
      clojure.lang.ISeq
      clojure.lang.Seqable
      (cons [_ a]
        (cons a s))
      (next [this]
        (closeable-seq (next s) close-fn))
      (more [this]
        (let [rst (next this)]
          (if (empty? rst)
            '()
            rst)))
      (first [_]
        (first s))
      (seq [this]
        (cons (first this) (next this))))))

;;;

(defn options
  "Options that can be used as an argument to `create-db`."
  [& {:keys [create-if-missing?
             write-buffer-size
             block-size
             max-open-files
             cache-size
             comparator
             paranoid-checks?
             logger]
      :or {create-if-missing? true}}]
  (let [options (doto (Options.)
                  (.createIfMissing create-if-missing?))]
    options))

(defn create-db
  ([file]
     (create-db file nil))
  ([file opts]
     (.open JniDBFactory/factory (io/file file) (or opts (options)))))

(defn get
  "Returns the value of `key` for the given database or snapshot, as a byte-array.  If the key doesn't
   exist, returns nil."
  [db-or-snapshot key]
  (condp instance? db-or-snapshot
    DB       (.get ^DB db-or-snapshot (bs/to-byte-array key))
    Snapshot (.get
               ^DB (.db ^Snapshot db-or-snapshot) (bs/to-byte-array key)
               (.read-options ^Snapshot db-or-snapshot))))

(defn snapshot
  "Returns a snapshot of the database that can be used with `get` and `iterator`."
  [^DB db]
  (->Snapshot
    db
    (doto (ReadOptions.)
      (.snapshot (.getSnapshot db)))))

(defn iterator
  "Returns a closeable sequence of map entries (accessed with `key` and `val`) that is the inclusive range
   from `start` to `end`."
  ([db-or-snapshot]
     (iterator db-or-snapshot nil nil))
  ([db-or-snapshot start]
     (iterator db-or-snapshot start nil))
  ([db-or-snapshot start end]
     (let [iterator (condp instance? db-or-snapshot
                      DB       (.iterator ^DB db)
                      Snapshot (.iterator
                                 ^DB (.db ^Snapshot db-or-snapshot)
                                 ^ReadOptions (.read-options ^Snapshot db-or-snapshot)))]
       (if start
         (.seek ^DBIterator iterator (bs/to-byte-array start))
         (.seekToFirst ^DBIterator iterator))

       (let [s (iterator-seq iterator)
             s (if end
                 (let [end (bs/to-byte-array end)]
                   (take-while
                     #(not (pos? (bs/compare-bytes (key %) end)))
                     s))
                 s)]
         (closeable-seq s #(.close ^Closeable iterator))))))

(defn put
  "Puts one or more key/value pairs into the given `db`.  These keys and values can be anything
   that can be transformed into a byte-array."
  ([^DB db key val]
     (.put db
       (bs/to-byte-array key)
       (bs/to-byte-array val)))
  ([^DB db key val & key-vals]
     (with-open [batch (.createWriteBatch db)]
       (.put batch
         (bs/to-byte-array key)
         (bs/to-byte-array val))
       (doseq [[k v] (partition 2 key-vals)]
         (.put batch
           (bs/to-byte-array k)
           (bs/to-byte-array v)))
       (.write db batch))))

(defn delete
  "Deletes one or more keys in the given `db`.  The keys can be anything that can be transformed
   into a byte-array."
  ([^DB db key]
     (.delete db (bs/to-byte-array key)))
  ([^DB db key & keys]
     (with-open [batch (.createWriteBatch db)]
       (.delete batch
         (bs/to-byte-array key))
       (doseq [k keys]
         (.delete batch
           (bs/to-byte-array k)))
       (.write db batch))))


