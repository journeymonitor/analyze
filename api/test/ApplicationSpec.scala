import java.io.File

import org.scalatestplus.play._
import play.api
import play.api.test.Helpers._
import play.api.test._
import play.api.{ApplicationLoader, Environment, Mode}

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

  }

}
