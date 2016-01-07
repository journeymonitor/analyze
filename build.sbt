name := "journeymonitor-analyze"

val commonSettings = Seq(
  organization := "com.journeymonitor",
  version := "0.1",
  javacOptions := Seq("-source", "1.8", "-target", "1.8"),
  scalaVersion := "2.11.7",
  scalacOptions := Seq("-target:jvm-1.8", "-unchecked", "-deprecation", "-encoding", "utf8"),
  parallelExecution in Test := false,
  // stole the following from https://github.com/datastax/spark-cassandra-connector/pull/858/files
  // in order to avoid assembly merge errors with netty
  assemblyMergeStrategy in assembly <<= (mergeStrategy in assembly) { (old) =>
    {
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
      case x => old(x)
    }
  }
)

lazy val testDependencies = Seq (
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)

lazy val cassandraDependencies = Seq (
  "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.8",
  "com.chrisomeara" % "pillar_2.11" % "2.0.1"
)

lazy val json4sDependencies = Seq (
  "org.json4s" %% "json4s-jackson" % "3.2.10"
)

lazy val sparkDependencies = Seq (
  "org.apache.spark" %% "spark-core" % "1.5.1" % "provided",
  "com.datastax.spark" %% "spark-cassandra-connector" % "1.5.0-M2"
)


lazy val spark = project.in(file("spark"))
  .settings(commonSettings:_*)
  .settings(
    javacOptions := Seq("-source", "1.7", "-target", "1.7"),
    scalacOptions := Seq("-target:jvm-1.7", "-unchecked", "-deprecation", "-encoding", "utf8")
  )
  .settings(libraryDependencies ++= (sparkDependencies ++ json4sDependencies ++ testDependencies))
  .settings(assemblyJarName in assembly := "journeymonitor-analyze-spark-assembly.jar")

lazy val importer = project.in(file("importer"))
  .settings(commonSettings:_*)
  .settings(libraryDependencies ++= (json4sDependencies ++ testDependencies ++ cassandraDependencies))
  .settings(assemblyJarName in assembly := "journeymonitor-analyze-importer-assembly.jar")
  .dependsOn(common)

val common = project
  .settings(commonSettings:_*)
  .settings(libraryDependencies ++= (testDependencies ++ cassandraDependencies))

lazy val api = project
  .settings(commonSettings:_*)
  .dependsOn(common)

lazy val main = project.in(file("."))
  .aggregate(common, spark, importer, api)
