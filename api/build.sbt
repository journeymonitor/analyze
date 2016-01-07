name := """api"""

version := "1.0-SNAPSHOT"

enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.scalatestplus" %% "play" % "1.4.0-M4" % "test",
  "com.datastax.cassandra" % "cassandra-driver-core" % "2.1.8"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator
