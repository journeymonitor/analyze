package com.journeymonitor.analyze.common

import com.datastax.driver.core.Session
import de.kaufhof.pillar.{Migrator, Parser, Registry, SimpleStrategy}

object Pillar {

  private val registry = Registry(loadMigrationsFromJarOrFilesystem())
  private val migrator = Migrator(registry)

  private def loadMigrationsFromJarOrFilesystem() = {
    val migrationsDir = "migrations/"
    val migrationNames = JarUtils.getResourceListing(getClass, migrationsDir).toList.filter(_.nonEmpty)
    val parser = Parser()

    migrationNames.map(name => getClass.getClassLoader.getResourceAsStream(migrationsDir + name)).map {
      stream =>
        try {
          parser.parse(stream)
        } finally {
          stream.close()
        }
    }.toList
  }

  def initialize(session: Session, keyspace: String, replicationFactor: Int): Unit = {
    migrator.initialize(
      session,
      keyspace,
      SimpleStrategy(replicationFactor)
    )
  }

  def migrate(session: Session): Unit = {
    migrator.migrate(session)
  }
}
