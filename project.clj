(defproject factual/clj-rocksdb "0.1.1-SNAPSHOT"
  :description "an idiomatic wrapper for RocksDB"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[factual/rocksdbjni-all "99-master-SNAPSHOT-uber"]
                 [org.iq80.leveldb/leveldb-api "0.7"]
                 [byte-streams "0.1.10"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]
                                  [criterium "0.4.3"]
                                  [codox-md "0.2.0" :exclusions [org.clojure/clojure]]]}}
  :plugins [[codox "0.6.4"]]
  :codox {:writer codox-md.writer/write-docs
          :include [clj-leveldb]})
