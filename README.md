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

Pillar-based Cassandra migration scripts live in `common/src/main/resources/migrations`. They are automatically applied
whenever an application starts that uses the `CassandraClient` object.


#### Feed sample testresults into local Cassandra

- `sbt assembly`
- `java -jar importer/target/scala-2.11/journeymonitor-analyze-importer-assembly.jar ./testresults.json`


#### Run HAR analyzer on local Spark cluster

- Create fat jar of this app with all dependencies included with `sbt assembly`
- cd to the root folder of your Spark installation
- `./sbin/start-master.sh --host 127.0.0.1`
- `./sbin/start-slave.sh spark://127.0.0.1:7077`
- `./bin/spark-submit --deploy-mode client --master spark://127.0.0.1:7077 --executor-memory 12g PATH/TO/APPDIR/spark/target/scala-2.11/journeymonitor-analyze-spark-assembly.jar`


#### Run Spark shell with dependencies

- cd to the root folder of your Spark installation
- `./bin/spark-shell --packages com.datastax.spark:spark-cassandra-connector_2.11:1.5.0-M2,org.json4s:json4s-native_2.11:3.3.0`


### Spark behaviour

This is how an executor queries the database:

    SELECT
        "testcase_id", "datetime_run", "har", "is_analyzed", "testresult_id"
    FROM "analyze"."testresults"
    WHERE token("testresult_id") > ? AND token("testresult_id") <= ? ALLOW FILTERING

`spark-submit --deploy-mode client` -> The driver process runs on the local machine and connects to the master in order
to address workers.

`spark-submit --deploy-mode cluster` -> The master chooses a worker instance, where the driver process is then started.
In order for this to work, each worker needs the driver program jar.

The driver needs access to the C* cluster in order to read meta information needed to partition the C* data on the
Spark cluster (see http://stackoverflow.com/questions/33897586/apache-spark-driver-instead-of-just-the-executors-tries-to-connect-to-cassand).

Thesis: The executors on the workers do not run the complete jar. This is only run by the driver, who transfers
        operation definitions to the workers. Therefore, something like `println` only prints to stdout/stderr on the
        system that runs the driver. Also, `RDD.foreach` pulls each RDD entry into the driver. If, e.g., the application
        code writes to a local file, this too happens on the system that runs the driver process only.
        -> True

Thesis: conf.set("spark.cassandra.connection.host", "1.2.3.4") only defines the initial driver connection to the C*
        cluster - afterwards, the C* cluster topology is known and used for optimal connections from executors
        to C* nodes (see https://github.com/datastax/spark-cassandra-connector/blob/master/doc/1_connecting.md#connection-management)
        -> True
