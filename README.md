# Notes

## Hints for local development environment

### Feed sample testresults into local Cassandra

- `sbt assembly`
- `java -jar importer/target/scala-2.11/importer-assembly-0.1.jar ./testresults.json`


### Run HAR analyzer on local Spark cluster

- Create fat jar of this app with all dependencies included with `sbt assembly`
- cd to the root folder of your Spark installation
- `./sbin/start-master.sh --host 127.0.0.1`
- `./sbin/start-slave.sh spark://127.0.0.1:7077`
- `./bin/spark-submit --deploy-mode cluster --master spark://127.0.0.1:6066 PATH/TO/APPDIR/spark/target/scala-2.11/spark-assembly-0.1.jar`


### Run Spark shell with dependencies

- cd to the root folder of your Spark installation
- `./bin/spark-shell --packages com.datastax.spark:spark-cassandra-connector_2.11:1.5.0-M2,org.json4s:json4s-native_2.11:3.3.0`