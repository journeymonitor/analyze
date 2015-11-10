name := "JourneyMonitor - analyze"

val commonSettings = Seq(
  organization := "com.journeymonitor",
  version := "0.1",
  scalaVersion := "2.11.7",
  scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")
)

lazy val spark = project.in(file("spark"))
  .settings(commonSettings:_*)
  .settings(libraryDependencies ++= sparkDependencies)
  // stole the following from https://github.com/datastax/spark-cassandra-connector/pull/858/files
  // in order to avoid assembly merge errors with netty
  .settings(assemblyMergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
    {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
      case x => old(x)
    }
  })

lazy val importer = project.in(file("importer"))
  .settings(commonSettings:_*)

lazy val main = project.in(file("."))
  .aggregate(spark, importer)


lazy val sparkDependencies = Seq (
  "org.apache.spark" %% "spark-core" % "1.5.1" % "provided",
  "org.apache.spark" %% "spark-sql" % "1.5.1" % "provided",
  "com.datastax.spark" %% "spark-cassandra-connector" % "1.5.0-M2",
  "org.json4s" %% "json4s-native" % "3.3.0"
)
