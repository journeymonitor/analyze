name := "journeymonitor-analyze"

val commonSettings = Seq(
  organization := "com.journeymonitor",
  version := "0.1",
  javacOptions := Seq("-source", "1.8", "-target", "1.8"),
  scalaVersion := "2.11.8",
  scalacOptions := Seq("-target:jvm-1.8", "-unchecked", "-deprecation", "-encoding", "utf8"),
  // stole the following from https://github.com/datastax/spark-cassandra-connector/pull/858/files
  // in order to avoid assembly merge errors with netty
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  }
)

lazy val testDependencies = Seq (
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)

lazy val cassandraDependencies = Seq (
  "com.datastax.cassandra" % "cassandra-driver-core" % "3.1.2",
  // Exclusion of jline needed to avoid assembly deduplication errors for libjansi.so, libjansi.jnilib, jansi.dll
  "de.kaufhof" %% "pillar" % "3.3.0" exclude("jline", "jline")
)

lazy val json4sDependencies = Seq (
  "org.json4s" %% "json4s-jackson" % "3.2.10"
)


lazy val sparkDependencies = Seq (
  "org.apache.spark" %% "spark-core" % "1.5.1" % "provided",
  // Force netty version.  This avoids some Spark netty dependency problem ("java.lang.VerifyError: class io.netty.channel.nio.NioEventLoop overrides final method pendingTasks.()I")
  "io.netty" % "netty-all" % "4.0.37.Final",
  // 1.5.0 and 1.5.1 result in "missing or invalid dependency detected while loading class file 'package.class'." (see https://datastax-oss.atlassian.net/browse/SPARKC-358)
  "com.datastax.spark" %% "spark-cassandra-connector" % "1.5.0-RC1"
)


lazy val common = project
  .settings(commonSettings:_*)
  .settings(
    javacOptions := Seq("-source", "1.7", "-target", "1.7"),
    scalacOptions := Seq("-target:jvm-1.7", "-unchecked", "-deprecation", "-encoding", "utf8")
  )
  .settings(libraryDependencies ++= (testDependencies ++ cassandraDependencies))

lazy val spark = project.in(file("spark"))
  .settings(commonSettings:_*)
  .settings(
    javacOptions := Seq("-source", "1.7", "-target", "1.7"),
    scalacOptions := Seq("-target:jvm-1.7", "-unchecked", "-deprecation", "-encoding", "utf8")
  )
  .settings(libraryDependencies ++= (sparkDependencies ++ json4sDependencies ++ testDependencies))
  .settings(assemblyJarName in assembly := "journeymonitor-analyze-spark-assembly.jar")
  .dependsOn(common)

lazy val importer = project.in(file("importer"))
  .settings(commonSettings:_*)
  .settings(libraryDependencies ++= (json4sDependencies ++ testDependencies ++ cassandraDependencies))
  .settings(assemblyJarName in assembly := "journeymonitor-analyze-importer-assembly.jar")
  .dependsOn(common)

lazy val api = project
  .settings(commonSettings:_*)
  .dependsOn(common)

lazy val main = project.in(file("."))
  .aggregate(common, spark, importer, api)
