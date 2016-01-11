package components

import com.datastax.driver.core.Session
import com.journeymonitor.analyze.common.{CassandraClient, CassandraConnectionUri}
import models.Statistics
import play.api.inject.ApplicationLifecycle
import play.api.{Mode, Configuration, Environment}
import repositories.{StatisticsRepository, Repository}

import scala.concurrent.Future


trait CassandraRepositoryComponents {
  // These will be filled by Play's built-in components; should be `def` to avoid initialization problems
  def environment: Environment
  def configuration: Configuration
  def applicationLifecycle: ApplicationLifecycle

  lazy private val cassandraSession: Session = {
    val session: Session = environment.mode match {
      case Mode.Test => {
        val uriString = sys.env.getOrElse("JOURNEYMONITOR_ANALYZE_CASSANDRAURI_TEST", "cassandra://localhost:9042/test")
        val uri = CassandraConnectionUri(uriString)
        CassandraClient.createSessionAndInitKeyspace(uri)
      }
      case _ =>
        val uriString = sys.env.getOrElse("JOURNEYMONITOR_ANALYZE_CASSANDRAURI", "cassandra://localhost:9042/analyze")
        val uri = CassandraConnectionUri(uriString)
        CassandraClient.createSessionAndInitKeyspace(uri)
    }
    // Shutdown the client when the app is stopped or reloaded
    applicationLifecycle.addStopHook(() => Future.successful(session.close()))
    session
  }

  lazy val statisticsRepository: Repository[Statistics, String] = {
    new StatisticsRepository(cassandraSession)
  }
}
