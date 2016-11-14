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
- `make migrations`

Pillar-based Cassandra migration scripts live in `common/src/main/resources/migrations`. They are automatically applied
whenever an application starts that uses the `CassandraClient` object.


#### Feed sample testresults into local Cassandra

- `sbt assembly`
- `java -jar importer/target/scala-2.11/journeymonitor-analyze-importer-assembly.jar ./testresults.json`


#### Populate local Cassandra with dummy statistics

- `for i in `seq 1970 2015`; do for j in `seq -w 1 1 28`; do for k in `seq -w 1 1 59`; do echo "insert into statistics (testcase_id, day_bucket, testresult_id, testresult_datetime_run, number_of_200) values ('a', 'YEAR-02-DAY', 'trDAY-YEAR', 'YEAR-02-DAY 12:MINUTE:31+0000', 15);" | sed "s/YEAR/$i/g" | sed "s/DAY/$j/g" | sed "s/MINUTE/$k/g"; done; done; done | cqlsh -k analyze`


#### Run HAR analyzer on local Spark cluster

- Create fat jar of this app with all dependencies included with `sbt assembly`
- cd to the root folder of your Spark installation
- `./sbin/start-master.sh --host 127.0.0.1`
- `./sbin/start-slave.sh spark://127.0.0.1:7077`
- `./bin/spark-submit --deploy-mode client --master spark://127.0.0.1:7077 --executor-memory 12g PATH/TO/APPDIR/spark/target/scala-2.11/journeymonitor-analyze-spark-assembly.jar`


#### Run Spark shell with dependencies

- cd to the root folder of your Spark installation
- `./bin/spark-shell --packages com.datastax.spark:spark-cassandra-connector_2.11:1.5.0-M2,org.json4s:json4s-native_2.11:3.3.0`

On the production cluster master:
- `source /etc/journeymonitor/app-analyze-env.sh`
- `cd /opt/spark-1.5.1-bin-hadoop-2.6_scala-2.11`
- `./bin/spark-shell --packages com.datastax.spark:spark-cassandra-connector_2.11:1.5.0-M2,org.json4s:json4s-native_2.11:3.3.0 --master spark://service-misc-experiments-1:7077 --conf spark.cassandra.connection.host=$JOURNEYMONITOR_ANALYZE_SPARK_CASSANDRAHOST`


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


### Performance observations

At commit xyz (March 17, 2016):

- 3x Spark worker nodes: 6 GB RAM, 4x 2GHz Core i7, SSD-based Ceph Storage (per node)
- 1x Spark master node, running the analyze api application: 4 GB RAM, 2x 2GHz Core i7, SSD-based Ceph Storage

Around 2,5 GB of data on each C* node, with ~2524 keys per node for `analyze.statistics` table.

Each primary key combination (1 testcase_id for 1 day_bucket) contains 288 rows on the cluster for testcases that run
every 5 minutes.

`analyze.statistics` schema:

    CREATE TABLE analyze.statistics (
        testcase_id text,
        day_bucket text,
        testresult_datetime_run timestamp,
        number_of_200 int,
        number_of_400 int,
        number_of_500 int,
        runtime_milliseconds int,
        testresult_id text,
        PRIMARY KEY ((testcase_id, day_bucket), testresult_datetime_run)
    )

`nodetool cfstats -H analyze.statistics`

    Keyspace: analyze
        Read Count: 150983
        Read Latency: 0.1523509203022857 ms.
        Write Count: 11333890
        Write Latency: 0.027030531706236784 ms.
        Pending Flushes: 0
            Table: statistics
            SSTable count: 2
            Space used (live): 35.33 MB
            Space used (total): 35.33 MB
            Space used by snapshots (total): 19.29 MB
            Off heap memory used (total): 15.77 KB
            SSTable Compression Ratio: 0.3869892763700816
            Number of keys (estimate): 2569
            Memtable cell count: 329280
            Memtable data size: 11.52 MB
            Memtable off heap memory used: 0 bytes
            Memtable switch count: 238
            Local read count: 150983
            Local read latency: 0.168 ms
            Local write count: 11333890
            Local write latency: 0.030 ms
            Pending flushes: 0
            Bloom filter false positives: 0
            Bloom filter false ratio: 0.00000
            Bloom filter space used: 3.16 KB
            Bloom filter off heap memory used: 3.14 KB
            Index summary off heap memory used: 1.3 KB
            Compression metadata off heap memory used: 11.33 KB
            Compacted partition minimum bytes: 643 bytes
            Compacted partition maximum bytes: 103.3 KB
            Compacted partition mean bytes: 38.06 KB
            Average live cells per slice (last five minutes): 8.0
            Maximum live cells per slice (last five minutes): 310
            Average tombstones per slice (last five minutes): 1.0
            Maximum tombstones per slice (last five minutes): 1


Result of running
`siege -c 50 -b http://service-misc-experiments-1.service.gkh-setu.de:8081/testcases/657D6D9E-7D59-472A-BD16-B291CC4573DC/statistics/latest/?minTestresultDatetimeRun=2016-03-17+08%3A05%3A12%2B0000`
which results in 1 C* query per request, from one of the Spark cluster nodes while no Spark job is running:

Transactions:             126404 hits
Availability:             100.00 %
Elapsed time:              90.18 secs
Data transferred:          22.30 MB
Response time:              0.04 secs
Transaction rate:        1401.69 trans/sec
Throughput:                 0.25 MB/sec
Concurrency:               49.92
Successful transactions:  126405
Failed transactions:           0
Longest transaction:        0.21
Shortest transaction:       0.00

-> Results in ~20% CPU load on the C* nodes


Result of running
`siege -c 50 -b http://service-misc-experiments-1.service.gkh-setu.de:8081/testcases/657D6D9E-7D59-472A-BD16-B291CC4573DC/statistics/latest/?minTestresultDatetimeRun=2016-03-07+08%3A05%3A12%2B0000`
which results in 10 C* query per request, from one of the Spark cluster nodes while no Spark job is running:

Transactions:               5666 hits
Availability:             100.00 %
Elapsed time:             109.13 secs
Data transferred:         126.19 MB
Response time:              0.96 secs
Transaction rate:          51.92 trans/sec
Throughput:                 1.16 MB/sec
Concurrency:               49.77
Successful transactions:    5666
Failed transactions:           0
Longest transaction:        3.46
Shortest transaction:       0.06

-> Results in ~60% CPU load on the C* nodes
