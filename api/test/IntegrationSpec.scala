import java.io.File

import org.scalatestplus.play._
import play.api
import play.api.{Mode, Environment, ApplicationLoader}

class IntegrationSpec extends PlaySpec with OneBrowserPerSuite with OneServerPerSuite with HtmlUnitFactory {

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

      pageSource must include ("Your new application is ready. cassandra://localhost:9042/test")
    }
  }
}
