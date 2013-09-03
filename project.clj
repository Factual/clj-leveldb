(defproject com.factual/clj-leveldb "0.1.0-SNAPSHOT"
  :description "an idiomatic wrapper for LevelDB"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.fusesource.leveldbjni/leveldbjni-all "1.7"]
                 [org.iq80.leveldb/leveldb-api "0.6"]
                 [byte-streams "0.1.5"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.5.1"]
                                  [criterium "0.4.1"]]}})

