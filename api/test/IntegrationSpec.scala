import java.io.File

import com.journeymonitor.analyze.common.{CassandraClient, CassandraConnectionUri}
import org.scalatest.BeforeAndAfter
import org.scalatestplus.play._
import play.api
import play.api.{Mode, Environment, ApplicationLoader}

class IntegrationSpec extends PlaySpec with OneBrowserPerSuite with OneServerPerSuite with HtmlUnitFactory with BeforeAndAfter {

  "before" in {
    val uriString = sys.env.getOrElse("JOURNEYMONITOR_ANALYZE_CASSANDRAURI_TEST", "cassandra://localhost:9042/test")
    val uri = CassandraConnectionUri(uriString)
    val session = CassandraClient.createSessionAndInitKeyspace(uri)

    session.execute("INSERT INTO statistics (testcase_id, testresult_id, datetime_run, number_of_200) values ('testcase1', 'testresult1', '2016-01-07 07:32:12+0000', 123);")
  }

  override implicit lazy val app: api.Application =
    new AppLoader().load(
      ApplicationLoader.createContext(
        new Environment(
          new File("."), ApplicationLoader.getClass.getClassLoader, Mode.Test)
      )
    )

  "Application" should {

    "work from within a browser" in {

      go to "http://localhost:" + port

      pageSource must include ("Your new application is ready. testresult1")
    }
  }
}
