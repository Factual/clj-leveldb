(ns clj-leveldb
  (:refer-clojure :exclude [get sync])
  (:require
    [clojure.java.io :as io]
    [byte-streams :as bs])
  (:import
    [java.io
     Closeable]
    [org.fusesource.leveldbjni
     JniDBFactory]
    [org.iq80.leveldb
     WriteBatch
     DBIterator
     Options
     ReadOptions
     WriteOptions
     CompressionType
     DB
     Range]))

;;;

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
      clojure.lang.IPersistentCollection
      (equiv [this x]
        (loop [a this, b x]
          (if (or (empty? a) (empty? b))
            (and (empty? a) (empty? b))
            (if (= (first x) (first b))
              (recur (rest a) (rest b))
              false))))
      (empty [_]
        [])
      (count [this]
        (count (seq this)))
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
        this))))

(defn- iterator-seq- [^DBIterator iterator start end key-decoder key-encoder val-decoder]
  (if start
    (.seek ^DBIterator iterator (bs/to-byte-array (key-encoder start)))
    (.seekToFirst ^DBIterator iterator))

  (let [s (iterator-seq iterator)
        s (if end
            (let [end (bs/to-byte-array (key-encoder end))]
              (take-while
                #(not (pos? (bs/compare-bytes (key %) end)))
                s))
            s)]
    (closeable-seq
      (map
        #(vector
           (key-decoder (key %))
           (val-decoder (val %)))
        s)
      (reify
        Object
        (finalize [_] (.close iterator))
        clojure.lang.IFn
        (invoke [_] (.close iterator))))))

;;;

(defprotocol ILevelDB
  (^:private ^DB db-  [_])
  (^:private batch- [_])
  (^:private iterator- [_])
  (^:private get- [_ k])
  (^:private put- [_ k v options])
  (^:private del- [_ k options])
  (^:private iterator- [_ start end])
  (^:private batch- [_ options])
  (^:private snapshot- [_]))

(defrecord Snapshot
  [db
   key-decoder
   key-encoder
   val-decoder
   ^ReadOptions read-options]
  ILevelDB
  (snapshot- [this] this)
  (db- [_] (db- db))
  (get- [_ k]
    (val-decoder (.get (db- db) (bs/to-byte-array (key-encoder k)) read-options)))
  (iterator- [_ start end]
    (iterator-seq-
      (.iterator (db- db) read-options)
      start
      end
      key-decoder
      key-encoder
      val-decoder))
  Closeable
  (close [_]
    (-> read-options .snapshot .close))
  (finalize [this] (.close this)))

(defrecord Batch
  [^DB db
   ^WriteBatch batch
   key-encoder
   val-encoder
   ^WriteOptions options]
  ILevelDB
  (db- [_] db)
  (batch- [this _] this)
  (put- [_ k v _]
    (.put batch
      (bs/to-byte-array (key-encoder k))
      (bs/to-byte-array (val-encoder v))))
  (del- [_ k _]
    (.delete batch (bs/to-byte-array (key-encoder k))))
  Closeable
  (close [_]
    (if options
      (.write db batch options)
      (.write db batch))
    (.close batch)))

(defrecord LevelDB
  [^DB db
   key-decoder
   key-encoder
   val-decoder
   val-encoder]
  Closeable
  (close [_] (.close db))
  ILevelDB
  (db- [_]
    db)
  (get- [_ k]
    (let [k (bs/to-byte-array (key-encoder k))]
      (val-decoder (.get db k))))
  (put- [_ k v options]
    (let [k (bs/to-byte-array (key-encoder k))
          v (bs/to-byte-array (val-encoder v))]
      (if options
        (.put db k v options)
        (.put db k v))))
  (del- [_ k options]
    (let [k (bs/to-byte-array (key-encoder k))]
      (if options
        (.delete db k options)
        (.delete db k))))
  (snapshot- [this]
    (->Snapshot
      this
      key-decoder
      key-encoder
      val-decoder
      (doto (ReadOptions.)
        (.snapshot (.getSnapshot db)))))
  (batch- [this options]
    (->Batch
      db
      (.createWriteBatch db)
      key-encoder
      val-encoder
      options))
  (iterator- [_ start end]
    (iterator-seq-
      (.iterator db)
      start
      end
      key-decoder
      key-encoder
      val-decoder)))

;;;

(def ^:private option-setters
  {:create-if-missing? #(.createIfMissing ^Options %1 %2)
   :error-if-exists?   #(.errorIfExists ^Options %1 %2)
   :write-buffer-size  #(.writeBufferSize ^Options %1 %2)
   :block-size         #(.blockSize ^Options %1 %2)
   :block-restart-interval #(.blockRestartInterval ^Options %1 %2)
   :max-open-files     #(.maxOpenFiles ^Options %1 %2)
   :cache-size         #(.cacheSize ^Options %1 %2)
   :comparator         #(.comparator ^Options %1 %2)
   :paranoid-checks?   #(.paranoidChecks ^Options %1 %2)
   :compress?          #(.compressionType ^Options %1 (if % CompressionType/SNAPPY CompressionType/NONE))
   :logger             #(.logger ^Options %1 %2)})

(defn create-db
  "Creates a closeable database object, which takes a directory and zero or more options.

   The key and val encoder/decoders are functions for transforming to and from byte-arrays."
  [directory
   {:keys [key-decoder
           key-encoder
           val-decoder
           val-encoder
           create-if-missing?
           error-if-exists?
           write-buffer-size
           block-size
           max-open-files
           cache-size
           comparator
           compress?
           paranoid-checks?
           block-restart-interval
           logger]
    :or {key-decoder identity
         key-encoder identity
         val-decoder identity
         val-encoder identity
         compress? true
         cache-size (* 32 1024 1024)
         block-size (* 16 1024)
         write-buffer-size (* 32 1024 1024)
         create-if-missing? true
         error-if-exists? false}
    :as options}]
  (->LevelDB
    (.open JniDBFactory/factory
      (io/file directory)
      (let [opts (Options.)]
        (doseq [[k v] options]
          (when (and v (contains? option-setters k))
            ((option-setters k) opts v)))
        opts))
    key-decoder
    key-encoder
    val-decoder
    val-encoder))

(defn destroy-db
  "Destroys the database at the specified `directory`."
  [directory]
  (.destroy JniDBFactory/factory
    (io/file directory)
    (Options.)))

(defn repair-db
  "Repairs the database at the specified `directory`."
  [directory]
  (.repair JniDBFactory/factory
    (io/file directory)
    (Options.)))

;;;

(defn get
  "Returns the value of `key` for the given database or snapshot. If the key doesn't exist, returns
   `default-value` or nil."
  ([db key]
     (get db key nil))
  ([db key default-value]
     (let [v (get- db key)]
       (if (nil? v)
         default-value
         v))))

(defn snapshot
  "Returns a snapshot of the database that can be used with `get` and `iterator`. This implements
   java.io.Closeable, and can leak space in the database if not closed."
  [db]
  (snapshot- db))

(defn iterator
  "Returns a closeable sequence of map entries (accessed with `key` and `val`) that is the inclusive
   range from `start `to `end`.  If exhausted, the sequence is automatically closed."
  ([db]
     (iterator db nil nil))
  ([db start]
     (iterator db start nil))
  ([db start end]
     (iterator- db start end)))

(defn put
  "Puts one or more key/value pairs into the given `db`."
  ([db]
     )
  ([db key val]
     (put- db key val nil))
  ([db key val & key-vals]
     (with-open [^Batch batch (batch- db nil)]
       (put- batch key val nil)
       (doseq [[k v] (partition 2 key-vals)]
         (put- batch k v nil)))))

(defn delete
  "Deletes one or more keys in the given `db`."
  ([db]
     )
  ([db key]
     (del- db key nil))
  ([db key & keys]
     (with-open [^Batch batch (batch- db nil)]
       (del- batch key nil)
       (doseq [k keys]
         (del- batch k nil)))))

(defn sync
  "Forces the database to fsync."
  [db]
  (with-open [^Batch batch (batch- db (doto (WriteOptions.) (.sync true)))]
    ))

(defn stats
  "Returns statistics for the database."
  [db property]
  (.getProperty (db- db) "leveldb.stats"))

(defn bounds
  "Returns a tuple of the lower and upper keys in the database or snapshot."
  [db]
  (let [key-decoder (:key-decoder db)]
    (with-open [^DBIterator iterator (condp instance? db
                                       LevelDB (.iterator (db- db))
                                       Snapshot (.iterator (db- db) (:read-options db)))]
      (when (.hasNext (doto iterator .seekToFirst))
        [(-> (doto iterator .seekToFirst) .peekNext key key-decoder)
         (-> (doto iterator .seekToLast) .peekNext key key-decoder)]))))

(defn approximate-size
  "Returns an estimate of the size of entries, in bytes, inclusively between `start` and `end`."
  ([db]
     (apply approximate-size db (bounds db)))
  ([db start end]
     (let [key-encoder (:key-encoder db)]
       (first
         (.getApproximateSizes (db- db)
           (into-array
             [(Range.
                (bs/to-byte-array (key-encoder start))
                (bs/to-byte-array (key-encoder end)))]))))))

(defn compact
  "Forces compaction of database over the given range. If `start` or `end` are nil, they default to
   the full range of the database."
  ([db]
     (compact db nil nil))
  ([db start]
     (compact db start nil))
  ([db start end]
     (let [encoder (:key-encoder db)
           [start' end'] (bounds db)
           start (or start start')
           end (or end end')]
       (when (and start end)
         (.compactRange (db- db)
           (bs/to-byte-array (encoder start))
           (bs/to-byte-array (encoder end)))))))
