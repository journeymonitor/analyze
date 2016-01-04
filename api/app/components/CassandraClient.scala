package components

import play.api.inject.ApplicationLifecycle
import play.api.{Mode, Configuration, Environment}

import scala.concurrent.Future

trait CassandraClientComponent {
  // These will be filled by Play's built-in components; should be `def` to avoid initialization problems
  def environment: Environment
  def configuration: Configuration
  def applicationLifecycle: ApplicationLifecycle

  lazy val cassandraClient: String = {
    val client = environment.mode match {
      case Mode.Test =>
        "CassandraClient in Test Mode"
      case _ =>
        "CassandraClient not in Test Mode"
    }
    // Shutdown the client when the app is stopped or reloaded
    applicationLifecycle.addStopHook(() => Future.successful(println(""""Closed" the fake CassandraClient""")))
    client
  }
}
