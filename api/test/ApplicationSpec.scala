import java.io.File

import components.{Statistics, Repository}
import controllers.Application
import play.api
import play.api.{ApplicationLoader, Environment, Mode}
import play.api.ApplicationLoader.Context
import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._

import scala.concurrent.Future

class MockStatisticsRepository extends Repository[Statistics, String] {
  override def getOneById(id: String): Statistics = {
    Statistics("mocked-testresult-" + id, 123)
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

  "Application" should {

    "send 404 on a bad request" in {
      val Some(wrongRoute) = route(FakeRequest(GET, "/boum"))

      status(wrongRoute) mustBe NOT_FOUND
    }

    "render the index page" in {
      val Some(home) = route(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("Your new application is ready. mocked-testresult-testcase1")
    }

    "return a JSON object with statistics for a given testresult id" in {
      val Some(response) = route(FakeRequest(GET, "/testresults/abcd/statistics/"))

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe """{"foo":"a","bar":"b"}"""
    }
  }

}
