package components

import play.api.inject.ApplicationLifecycle
import play.api.{Mode, Configuration, Environment}

import scala.concurrent.Future

class CassandraClient(url: String) {
  val theUrl = this.url

  def close() {
    println(""""Closed" the fake CassandraClient for URL """ + url)
  }
}

trait CassandraClientComponent {
  // These will be filled by Play's built-in components; should be `def` to avoid initialization problems
  def environment: Environment
  def configuration: Configuration
  def applicationLifecycle: ApplicationLifecycle

  lazy val cassandraClient: CassandraClient = {
    val client = environment.mode match {
      case Mode.Test =>
        new CassandraClient("cassandra://localhost:9042/test")
      case _ =>
        new CassandraClient("cassandra://localhost:9042/prod")
    }
    // Shutdown the client when the app is stopped or reloaded
    applicationLifecycle.addStopHook(() => Future.successful(client.close()))
    client
  }
}
