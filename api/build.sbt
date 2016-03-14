name := """api"""

version := "1.0-SNAPSHOT"

enablePlugins(PlayScala)

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalatestplus" %% "play" % "1.4.0-M4" % "test",
  "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.8",
  "org.scassandra" % "java-client" % "0.11.0", // depending on this globally in /build.sbt results in "Akka JAR version [2.3.13] does not match the provided config version [2.3.9]"
  "com.typesafe.akka" %% "akka-testkit" % "2.4.2"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator
