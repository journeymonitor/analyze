import java.io.File

import com.journeymonitor.analyze.common.{CassandraClient, CassandraConnectionUri}
import org.scalatest.BeforeAndAfter
import org.scalatestplus.play._
import play.api
import play.api.{ApplicationLoader, Environment, Mode}

class IntegrationSpec extends PlaySpec with OneBrowserPerSuite with OneServerPerSuite with HtmlUnitFactory with BeforeAndAfter {

  before {
    val uriString = sys.env.getOrElse("JOURNEYMONITOR_ANALYZE_CASSANDRAURI_TEST", "cassandra://localhost:9042/test")
    val uri = CassandraConnectionUri(uriString)
    val session = CassandraClient.createSessionAndInitKeyspace(uri)

    session.execute("TRUNCATE statistics;")

    session.execute(
      """
        |INSERT INTO statistics
        | (testcase_id, day_bucket,   testresult_datetime_run,    testresult_id, runtime_milliseconds, number_of_200, number_of_400, number_of_500)
        | VALUES
        | ('testcase1', '2016-01-07', '2016-01-07 01:32:12+0000', 'testresult1', 111, 123, 456, 789);
      """.stripMargin
    )

    session.execute(
      """
        |INSERT INTO statistics
        | (testcase_id, day_bucket,   testresult_datetime_run,    testresult_id, runtime_milliseconds, number_of_200, number_of_400, number_of_500)
        | VALUES
        | ('testcase1', '2016-01-07', '2016-01-07 01:32:12+0000', 'testresult3', 333, 123, 456, 789);
      """.stripMargin
    )

    // Different testcase
    session.execute(
      """
        |INSERT INTO statistics
        | (testcase_id, day_bucket,   testresult_datetime_run,    testresult_id, runtime_milliseconds, number_of_200, number_of_400, number_of_500)
        | VALUES
        | ('testcase2', '2016-01-07', '2016-01-07 01:32:12+0000', 'testresult3', 333, 123, 456, 789);
      """.stripMargin
    )

    session.execute(
      """
        |INSERT INTO statistics
        | (testcase_id, day_bucket,   testresult_datetime_run,    testresult_id, runtime_milliseconds, number_of_200, number_of_400, number_of_500)
        | VALUES
        | ('testcase1', '2016-01-07', '2016-01-07 01:32:12+0000', 'testresult2', 222, 123, 456, 789);
      """.stripMargin
    )
  }

  override implicit lazy val app: api.Application =
    new AppLoader().load(
      ApplicationLoader.createContext(
        new Environment(
          new File("."), ApplicationLoader.getClass.getClassLoader, Mode.Test)
      )
    )

  "Integrated application" should {

    "render the index page" in {
      go to "http://localhost:" + port
      pageSource must include ("Your new application is ready.")
    }

    "return a JSON array with the latest statistics entry for a given testcase id" in {
      go to "http://localhost:" + port + "/testcases/testcase1/statistics/latest/?n=1"
      pageSource mustBe
        """
          |[
          |  {
          |    "testresultId":"testresult3",
          |    "runtimeMilliseconds":333,
          |    "numberOf200":123,
          |    "numberOf400":456,
          |    "numberOf500":789
          |  }
          |]
          |""".stripMargin.replace("\n", "").replace(" ", "")
    }

    "return a JSON array with the latest N statistics entries for a given testcase id" in {
      go to "http://localhost:" + port + "/testcases/testcase1/statistics/latest/?n=2"
      pageSource mustBe
        """
          |[
          |  {
          |    "testresultId":"testresult3",
          |    "runtimeMilliseconds":333,
          |    "numberOf200":123,
          |    "numberOf400":456,
          |    "numberOf500":789
          |  },
          |  {
          |    "testresultId":"testresult2",
          |    "runtimeMilliseconds":222,
          |    "numberOf200":122,
          |    "numberOf400":452,
          |    "numberOf500":782
          |  }
          |]
          |""".stripMargin.replace("\n", "").replace(" ", "")
    }

    "return an empty JSON array if no statistics exist for a given testcase id" in {
      go to "http://localhost:" + port + "/testcases/testcaseFoo/statistics/latest/?n=2"
      pageSource mustBe "[]"
    }

  }
}
