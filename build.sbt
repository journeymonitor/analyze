name := "analyze"

version := "1.0"

scalaVersion := "2.11.7"

libraryDependencies += "org.apache.spark" %% "spark-core" % "1.5.1"
libraryDependencies += "org.apache.spark" %% "spark-sql" % "1.5.1"
libraryDependencies += "com.datastax.spark" %% "spark-cassandra-connector" % "1.5.0-M2"
libraryDependencies += "org.json4s" %% "json4s-native" % "3.3.0"
