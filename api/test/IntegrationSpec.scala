import org.scalatestplus.play._

class IntegrationSpec extends PlaySpec with OneBrowserPerSuite with OneServerPerSuite with HtmlUnitFactory {

  "Application" should {

    "work from within a browser" in {

      go to "http://localhost:" + port

      pageSource must include ("Your new application is ready.")
    }
  }
}
