# JourneyMonitor

## About this repository

Applications that power the analytics backend of http://journeymonitor.com.

[![Build Status](https://travis-ci.org/journeymonitor/analyze.svg?branch=master)](https://travis-ci.org/journeymonitor/analyze)


## About the JourneyMonitor project

Please see [ABOUT.md](https://github.com/journeymonitor/infra/blob/master/ABOUT.md) for more information.


## Notes

### Hints for local development environment

#### Prepare Cassandra

- `cqlsh -e "CREATE KEYSPACE IF NOT EXISTS test WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };"`
- `cqlsh -e "CREATE KEYSPACE IF NOT EXISTS analyze WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };"`


#### Feed sample testresults into local Cassandra

- `sbt assembly`
- `java -jar importer/target/scala-2.11/journeymonitor-analyze-importer-assembly.jar ./testresults.json`


#### Run HAR analyzer on local Spark cluster

- Create fat jar of this app with all dependencies included with `sbt assembly`
- cd to the root folder of your Spark installation
- `./sbin/start-master.sh --host 127.0.0.1`
- `./sbin/start-slave.sh spark://127.0.0.1:7077`
- `./bin/spark-submit --deploy-mode cluster --master spark://127.0.0.1:6066 --executor-memory 12g PATH/TO/APPDIR/spark/target/scala-2.11/journeymonitor-analyze-spark-assembly.jar`


#### Run Spark shell with dependencies

- cd to the root folder of your Spark installation
- `./bin/spark-shell --packages com.datastax.spark:spark-cassandra-connector_2.11:1.5.0-M2,org.json4s:json4s-native_2.11:3.3.0`
