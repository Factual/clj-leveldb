(defproject com.factual/clj-leveldb "0.1.0-SNAPSHOT"
  :repositories {"releases" {:url "http://maven.corp.factual.com/nexus/content/repositories/releases"
                             :sign-releases false}
                 "snapshots" {:url "http://maven.corp.factual.com/nexus/content/repositories/snapshots"
                              :sign-releases false}
                 "public" "http://maven.corp.factual.com/nexus/content/groups/public/"}
  :description "an idiomatic wrapper for LevelDB"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.fusesource.leveldbjni/leveldbjni-all "1.7"]
                 [byte-streams "0.1.5-SNAPSHOT"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.5.1"]
                                  [criterium "0.4.1"]]}})

