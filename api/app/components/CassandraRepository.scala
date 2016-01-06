package components

import play.api.inject.ApplicationLifecycle
import play.api.{Mode, Configuration, Environment}

import scala.concurrent.Future

abstract class Model
case class Statistics(testresultId: String, numberOf200: Int) extends Model

class FakeCassandraClient(url: String) {
  def close() {
    println(""""Closed" the fake CassandraClient for URL """ + url)
  }
}

abstract class CassandraRepository[M <: Model, I](cassandraClient: FakeCassandraClient) extends Repository[M, I] {

  def rowToModel(row: Array[String]): M

  def getOneRowById(id: I): Array[String] = {
    // query using cassandraClient and return
    Array("testresult-" + id, "123")
  }

  override def getOneById(id: I): M = {
    val row = getOneRowById(id)
    rowToModel(row)
  }
}

abstract trait Repository[M <: Model, I] {
  def getOneById(id: I): M
}

class StatisticsRepository(cassandraClient: FakeCassandraClient) extends CassandraRepository[Statistics, String](cassandraClient) {
  override def rowToModel(row: Array[String]): Statistics = {
    Statistics(row(0), row(1).toInt)
  }
}

trait CassandraRepositoryComponents {
  // These will be filled by Play's built-in components; should be `def` to avoid initialization problems
  def environment: Environment
  def configuration: Configuration
  def applicationLifecycle: ApplicationLifecycle

  lazy private val cassandraClient: FakeCassandraClient = {
    val client = environment.mode match {
      case Mode.Test =>
        new FakeCassandraClient("cassandra://localhost:9042/test")
      case _ =>
        new FakeCassandraClient("cassandra://localhost:9042/prod")
    }
    // Shutdown the client when the app is stopped or reloaded
    applicationLifecycle.addStopHook(() => Future.successful(client.close()))
    client
  }

  lazy val statisticsRepository: Repository[Statistics, String] = {
    new StatisticsRepository(cassandraClient)
  }
}
