import play.api.test._
import play.api.test.Helpers._
import org.scalatestplus.play._

class ApplicationSpec extends PlaySpec with OneAppPerSuite {

  "Application" should {

    "send 404 on a bad request" in {
      val Some(wrongRoute) = route(FakeRequest(GET, "/boum"))

      status(wrongRoute) mustEqual NOT_FOUND
    }

    "render the index page" in {
      val Some(home) = route(FakeRequest(GET, "/"))

      status(home) mustEqual OK
      contentType(home) mustEqual Some("text/html")
      contentAsString(home) must include ("Your new application is ready.")
    }
  }

}
