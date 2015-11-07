name := "analyze"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies += "org.apache.spark" %% "spark-core" % "1.5.1" % "provided"
libraryDependencies += "com.datastax.spark" %% "spark-cassandra-connector" % "1.3.1"
