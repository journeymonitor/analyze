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
        | ('testcase1', '2016-01-01', '2016-01-01 01:32:12+0000', 'testresult1a', 111, 123, 456, 789);
      """.stripMargin
    )

    session.execute(
      """
        |INSERT INTO statistics
        | (testcase_id, day_bucket,   testresult_datetime_run,    testresult_id, runtime_milliseconds, number_of_200, number_of_400, number_of_500)
        | VALUES
        | ('testcase1', '2016-01-01', '2016-01-01 02:32:12+0000', 'testresult1b', 111, 123, 456, 789);
      """.stripMargin
    )

    session.execute(
      """
        |INSERT INTO statistics
        | (testcase_id, day_bucket,   testresult_datetime_run,    testresult_id, runtime_milliseconds, number_of_200, number_of_400, number_of_500)
        | VALUES
        | ('testcase1', '2016-01-02', '2016-01-02 01:32:12+0000', 'testresult2', 222, 123, 456, 789);
      """.stripMargin
    )

    session.execute(
      """
        |INSERT INTO statistics
        | (testcase_id, day_bucket,   testresult_datetime_run,    testresult_id, runtime_milliseconds, number_of_200, number_of_400, number_of_500)
        | VALUES
        | ('testcase1', '2016-01-03', '2016-01-03 01:32:12+0000', 'testresult3', 333, 123, 456, 789);
      """.stripMargin
    )

    // Different testcase
    session.execute(
      """
        |INSERT INTO statistics
        | (testcase_id, day_bucket,   testresult_datetime_run,    testresult_id, runtime_milliseconds, number_of_200, number_of_400, number_of_500)
        | VALUES
        | ('testcase2', '2016-01-01', '2016-01-01 01:32:12+0000', 'testresult3', 333, 123, 456, 789);
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

    "return a JSON array with all statistics entries for a given testcase id when not limited" in {
      go to "http://localhost:" + port + "/testcases/testcase1/statistics/latest/"
      pageSource mustBe
        """
          |[
          |  {
          |    "testresultId":"testresult3",
          |    "testresultDatetimeRun":"2016-01-03<space>02:32:12+0100",
          |    "runtimeMilliseconds":333,
          |    "numberOf200":123,
          |    "numberOf400":456,
          |    "numberOf500":789
          |  },
          |  {
          |    "testresultId":"testresult2",
          |    "testresultDatetimeRun":"2016-01-02<space>02:32:12+0100",
          |    "runtimeMilliseconds":222,
          |    "numberOf200":123,
          |    "numberOf400":456,
          |    "numberOf500":789
          |  },
          |  {
          |    "testresultId":"testresult1b",
          |    "testresultDatetimeRun":"2016-01-01<space>03:32:12+0100",
          |    "runtimeMilliseconds":111,
          |    "numberOf200":123,
          |    "numberOf400":456,
          |    "numberOf500":789
          |  },
          |  {
          |    "testresultId":"testresult1a",
          |    "testresultDatetimeRun":"2016-01-01<space>02:32:12+0100",
          |    "runtimeMilliseconds":111,
          |    "numberOf200":123,
          |    "numberOf400":456,
          |    "numberOf500":789
          |  }
          |]
          |""".stripMargin.replace("\n", "").replace(" ", "").replace("<space>", " ")
    }

    "return a JSON array with all statistics entries for a given testcase id when limited to the datetime of the earliest row" in {
      go to "http://localhost:" + port + "/testcases/testcase1/statistics/latest/?minTestresultDatetimeRun=2016-01-01+01%3A32%3A12%2B0000"
      pageSource mustBe
        """
          |[
          |  {
          |    "testresultId":"testresult3",
          |    "testresultDatetimeRun":"2016-01-03<space>02:32:12+0100",
          |    "runtimeMilliseconds":333,
          |    "numberOf200":123,
          |    "numberOf400":456,
          |    "numberOf500":789
          |  },
          |  {
          |    "testresultId":"testresult2",
          |    "testresultDatetimeRun":"2016-01-02<space>02:32:12+0100",
          |    "runtimeMilliseconds":222,
          |    "numberOf200":123,
          |    "numberOf400":456,
          |    "numberOf500":789
          |  },
          |  {
          |    "testresultId":"testresult1b",
          |    "testresultDatetimeRun":"2016-01-01<space>03:32:12+0100",
          |    "runtimeMilliseconds":111,
          |    "numberOf200":123,
          |    "numberOf400":456,
          |    "numberOf500":789
          |  },
          |  {
          |    "testresultId":"testresult1a",
          |    "testresultDatetimeRun":"2016-01-01<space>02:32:12+0100",
          |    "runtimeMilliseconds":111,
          |    "numberOf200":123,
          |    "numberOf400":456,
          |    "numberOf500":789
          |  }
          |]
          |""".stripMargin.replace("\n", "").replace(" ", "").replace("<space>", " ")
    }

    "return a JSON array with all but the earliest statistics entries for a given testcase id when limited to the datetime of the earliest row plus 1 second" in {
      go to "http://localhost:" + port + "/testcases/testcase1/statistics/latest/?minTestresultDatetimeRun=2016-01-01+01%3A32%3A13%2B0000"
      pageSource mustBe
        """
          |[
          |  {
          |    "testresultId":"testresult3",
          |    "testresultDatetimeRun":"2016-01-03<space>02:32:12+0100",
          |    "runtimeMilliseconds":333,
          |    "numberOf200":123,
          |    "numberOf400":456,
          |    "numberOf500":789
          |  },
          |  {
          |    "testresultId":"testresult2",
          |    "testresultDatetimeRun":"2016-01-02<space>02:32:12+0100",
          |    "runtimeMilliseconds":222,
          |    "numberOf200":123,
          |    "numberOf400":456,
          |    "numberOf500":789
          |  },
          |  {
          |    "testresultId":"testresult1b",
          |    "testresultDatetimeRun":"2016-01-01<space>03:32:12+0100",
          |    "runtimeMilliseconds":111,
          |    "numberOf200":123,
          |    "numberOf400":456,
          |    "numberOf500":789
          |  }
          |]
          |""".stripMargin.replace("\n", "").replace(" ", "").replace("<space>", " ")
    }

    "return an empty JSON array when limited to a datetime for which only older entries exist" in {
      go to "http://localhost:" + port + "/testcases/testcase1/statistics/latest/?minTestresultDatetimeRun=2016-01-03+01%3A32%3A13%2B0000"
      pageSource mustBe "[]"
    }

    "return an empty JSON array for a given testcase id where no testcase exists" in {
      go to "http://localhost:" + port + "/testcases/thisTestcaseDoesNotExist/statistics/latest/"
      pageSource mustBe "[]"
    }

  }
}
