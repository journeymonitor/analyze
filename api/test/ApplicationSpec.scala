import java.io.File
import java.util.Date
import com.journeymonitor.analyze.common.models.StatisticsModel
import com.journeymonitor.analyze.common.repositories.{ModelIterator, StatisticsRepository, Repository}
import org.scalatestplus.play._
import play.api
import play.api.ApplicationLoader.Context
import play.api.test.Helpers._
import play.api.test._
import play.api.{ApplicationLoader, Environment, Mode}
import scala.util.Try

class MockStatisticsRepository extends Repository[StatisticsModel, String] with StatisticsRepository {

  class MockModelIterator(testcaseId: String) extends ModelIterator {
    def next(): Try[StatisticsModel] = {
      Try {
        StatisticsModel("mocked-" + testcaseId, new Date(1456006032), 987, 123, 456, 789)
      }
    }
  }

  override def getNById(id: String, n: Int): Try[List[StatisticsModel]] = {
    Try {
      id match {
        case "testcaseWithFailure" => throw new Exception("blubb")
        case "testcaseWithoutStatistics" => List.empty[StatisticsModel]
        case id => List(
          StatisticsModel("mocked-" + id, new Date(1456006032), 987, 123, 456, 789)
        )
      }
    }
  }

  override def getAllForTestcaseIdSinceDatetime(testcaseId: String, datetime: java.util.Date): ModelIterator = {
    new MockModelIterator(testcaseId)
  }
}

class FakeApplicationComponents(context: Context) extends AppComponents(context) {
  override lazy val statisticsRepository = {
    new MockStatisticsRepository
  }
}

class FakeAppLoader extends ApplicationLoader {
  override def load(context: Context): api.Application =
    new FakeApplicationComponents(context).application
}

class ApplicationSpec extends PlaySpec with OneAppPerSuite {

  override implicit lazy val app: api.Application = {
    val appLoader = new FakeAppLoader
    appLoader.load(
      ApplicationLoader.createContext(
        new Environment(
          new File("."), ApplicationLoader.getClass.getClassLoader, Mode.Test)
      )
    )
  }

  "Non-integrated application" should {

    "send 404 on a bad request" in {
      val Some(wrongRoute) = route(FakeRequest(GET, "/boum"))

      status(wrongRoute) mustBe NOT_FOUND
    }

    "render the index page" in {
      val Some(home) = route(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Your new application is ready.")
    }

    "return a JSON array with the latest statistics entry for a given testcase id" in {
      val Some(response) = route(FakeRequest(GET, "/testcases/testcase1/statistics/latest/?minTestresultDatetimeRun=2016-01-21+22%3A03%3A49%2B0000"))

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe
        """
          |[{"testresultId":"mocked-testcase1",
          |"testresultDatetimeRun"
          |"runtimeMilliseconds":987,
          |"numberOf200":123,
          |"numberOf400":456,
          |"numberOf500":789}]
          |""".stripMargin.replace("\n", "")
    }

    "return a JSON object with an error message if there is a problem with the repository" in {
      val Some(response) = route(FakeRequest(GET, "/testcases/testcaseWithFailure/statistics/latest/?minTestresultDatetimeRun=2016-01-21+22%3A03%3A49%2B0000"))

      status(response) mustBe INTERNAL_SERVER_ERROR
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe """{"message":"An error occured"}"""
    }

    "return an empty JSON array if no statistics for a given testcase id exist" in {
      val Some(response) = route(FakeRequest(GET, "/testcases/testcaseWithoutStatistics/statistics/latest/?minTestresultDatetimeRun=2016-01-21+22%3A03%3A49%2B0000"))

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe "[]"
    }
  }

}
