package components

import play.api.inject.ApplicationLifecycle
import play.api.{Mode, Configuration, Environment}

import scala.concurrent.Future

case class Statistics(testresultId: String, numberOf200: Int)

class FakeCassandraClient(url: String) {
  val theUrl = this.url

  def close() {
    println(""""Closed" the fake CassandraClient for URL """ + url)
  }
}

trait CassandraRepository[Model, Id] extends Repository[Model, Id] {
  var cassandraClient: FakeCassandraClient = _

  override def getOneRowById(id: Id): Array[String] = {
    // query using cassandraClient and return
    Array("testresult-" + id, "123")
  }

  def setCassandraClient(cassandraClient: FakeCassandraClient): Unit = {
    this.cassandraClient = cassandraClient
  }
}

abstract trait Repository[Model, Id] {
  def getOneRowById(id: Id): Array[String]
  def rowToModel(row: Array[String]): Model

  def getOneById(id: Id): Model = {
    val row = getOneRowById(id)
    rowToModel(row)
  }
}

class StatisticsRepository extends CassandraRepository[Statistics, String] {
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
    val repo = new StatisticsRepository
    repo.setCassandraClient(cassandraClient)
    repo
  }
}
