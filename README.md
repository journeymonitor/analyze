# Notes

## Run on local Spark cluster
- Create fat jar of this app with all dependencies included with `sbt assembly`
- cd to the root folder of your Spark installation
- `./sbin/start-master.sh --host 127.0.0.1`
- `./sbin/start-slave.sh spark://127.0.0.1:7077`
- `./bin/spark-submit --deploy-mode cluster --master spark://127.0.0.1:6066 PATH/TO/APPDIR/spark/target/scala-2.11/spark-assembly-0.1.jar`
