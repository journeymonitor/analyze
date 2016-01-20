import java.io.File

import models.StatisticsModel
import org.scalatestplus.play._
import play.api
import play.api.ApplicationLoader.Context
import play.api.test.Helpers._
import play.api.test._
import play.api.{ApplicationLoader, Environment, Mode}
import repositories.Repository

class MockStatisticsRepository extends Repository[StatisticsModel, String] {
  override def getOneById(id: String): StatisticsModel = {
    StatisticsModel("mocked-testresult-" + id, 987, 123, 456, 789)
  }

  override def getNById(id: String, n: Int): List[StatisticsModel] = {
    List(
      StatisticsModel("mocked-testresult-" + id, 987, 123, 456, 789)
    )
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
      val Some(response) = route(FakeRequest(GET, "/testresults/abcd/statistics/latest/?n=1"))

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe
        """
          |[{"testresultId":"mocked-testresult-abcd",
          |"runtimeMilliseconds":987,
          |"numberOf200":123,
          |"numberOf400":456,
          |"numberOf500":789}]
          |""".stripMargin.replace("\n", "")
    }
  }

}
