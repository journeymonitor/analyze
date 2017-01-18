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
  "de.kaufhof" %% "pillar" % "3.3.0" exclude("jline", "jline")
)

lazy val json4sDependencies = Seq (
  "org.json4s" %% "json4s-jackson" % "3.2.10"
)


lazy val common = project
  .settings(commonSettings:_*)
  .settings(libraryDependencies ++= (testDependencies ++ cassandraDependencies))

lazy val importer = project.in(file("importer"))
  .settings(commonSettings:_*)
  .settings(libraryDependencies ++= (json4sDependencies ++ testDependencies ++ cassandraDependencies))
  .settings(assemblyJarName in assembly := "journeymonitor-analyze-importer-assembly.jar")
  .dependsOn(common)

lazy val api = project
  .settings(commonSettings:_*)
  .settings(libraryDependencies ++= cassandraDependencies)
  .dependsOn(common)

lazy val main = project.in(file("."))
  .aggregate(common, importer, api)

// "spark" is not an sbt sub-project, it's a standalone Maven project - see spark/pom.xml
