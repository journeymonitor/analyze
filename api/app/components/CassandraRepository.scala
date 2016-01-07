package components

import com.datastax.driver.core.Session
import com.datastax.driver.core.Row
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder._
import com.journeymonitor.analyze.common.{CassandraClient, CassandraConnectionUri}
import play.api.inject.ApplicationLifecycle
import play.api.{Mode, Configuration, Environment}

import scala.concurrent.Future

abstract class Model
case class Statistics(testresultId: String, numberOf200: Int) extends Model

abstract class CassandraRepository[M <: Model, I](session: Session, tablename: String) extends Repository[M, I] {
  def rowToModel(row: Row): M

  def getOneRowById(id: I): Row = {
    val selectStmt =
      select()
        .column("testresult_id")
        .column("number_of_200")
      .from(tablename)
      .where(QueryBuilder.eq("testcase_id", id))
      .limit(1)

    val resultSet = session.execute(selectStmt)
    val row = resultSet.one()
    row
  }

  override def getOneById(id: I): M = {
    val row = getOneRowById(id)
    rowToModel(row)
  }
}

abstract trait Repository[M <: Model, I] {
  def getOneById(id: I): M
}

class StatisticsRepository(session: Session) extends CassandraRepository[Statistics, String](session, "statistics") {
  override def rowToModel(row: Row): Statistics = {
    Statistics(row.getString("testresult_id"), row.getInt("number_of_200"))
  }
}

trait CassandraRepositoryComponents {
  // These will be filled by Play's built-in components; should be `def` to avoid initialization problems
  def environment: Environment
  def configuration: Configuration
  def applicationLifecycle: ApplicationLifecycle

  lazy private val cassandraSession: Session = {
    val session: Session = environment.mode match {
      case Mode.Test => {
        val uriString = sys.env.getOrElse("JOURNEYMONITOR_ANALYZE_API_CASSANDRAURI_TEST", "cassandra://localhost:9042/test")
        val uri = CassandraConnectionUri(uriString)
        CassandraClient.createSessionAndInitKeyspace(uri)
      }
      case _ =>
        val uriString = sys.env.getOrElse("JOURNEYMONITOR_ANALYZE_API_CASSANDRAURI", "cassandra://localhost:9042/analyze")
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
