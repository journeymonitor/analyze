package com.journeymonitor.analyze.api.test

import java.io.File
import java.util.{Calendar, Date}

import akka.stream.ActorMaterializer
import com.journeymonitor.analyze.api.AppComponents
import com.journeymonitor.analyze.common.models.StatisticsModel
import com.journeymonitor.analyze.common.repositories.StatisticsRepository
import org.scalatestplus.play._
import play.api
import play.api.ApplicationLoader.Context
import play.api.test.Helpers._
import play.api.test._
import play.api.{ApplicationLoader, Environment, Mode}
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}

import scala.util.Try

abstract class AkkaTestkitSupport extends TestKit(ActorSystem()) with ImplicitSender {
  implicit val materializer = ActorMaterializer()
  def terminateActorSystem() = system.terminate()
}

class MockStatisticsRepository extends StatisticsRepository {

  class MockEmptyModelIterator extends Iterator[StatisticsModel] {
    def next(): StatisticsModel = {
      throw new NoSuchElementException()
    }

    def hasNext = false
  }

  class MockModelIterator(testcaseId: String) extends Iterator[StatisticsModel] {
    var calls = 0
    def next(): StatisticsModel = {
      calls = calls + 1
      StatisticsModel("mocked-" + testcaseId + "-" + calls, new Date(1456006032000L + calls), 987, 123, 456, 789)
    }

    def hasNext = calls < 2
  }

  override def getAllForTestcaseIdSinceDatetime(testcaseId: String, datetime: java.util.Date): Try[Iterator[StatisticsModel]] = {
    // The Statistics controller must ensure that no matter what, the repository is only asked for the last 30 days at max.
    val calendar30DaysAgo = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    calendar30DaysAgo.add(Calendar.DATE, -30)
    calendar30DaysAgo.add(Calendar.MINUTE, -1) // Because we never now for sure how long test cases take, we add some buffer.
    val date30DaysAgo = calendar30DaysAgo.getTime
    if (datetime.before(date30DaysAgo)) {
      throw new Exception(s"Error in mock: minTestresultDatetimeRun was not limited - expected $date30DaysAgo, got $datetime.")
    }

    Try {
      if (testcaseId == "testcaseWithFailure") {
        throw new Exception("Error in mock")
      }
      if (testcaseId == "testcaseWithoutStatistics") {
        new MockEmptyModelIterator()
      } else {
        new MockModelIterator(testcaseId)
      }
    }
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

class StatisticsSpec extends PlaySpec with OneAppPerSuite {

  override lazy val app: api.Application = {
    val appLoader = new FakeAppLoader
    appLoader.load(
      ApplicationLoader.createContext(
        new Environment(
          new File("."), ApplicationLoader.getClass.getClassLoader, Mode.Test)
      )
    )
  }

  "Non-integrated statistics controller" should {

    "return a JSON array with the latest statistics entry for a given testcase id" in new AkkaTestkitSupport {
      val Some(response) = route(app, FakeRequest(GET, "/testcases/testcase1/statistics/latest/"))

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")

      contentAsString(response) mustBe
        """
          |[
          |{"testresultId":"mocked-testcase1-1",
          |"testresultDatetimeRun":"2016-02-20 22:07:12+0000",
          |"runtimeMilliseconds":987,
          |"numberOf200":123,
          |"numberOf400":456,
          |"numberOf500":789},
          |{"testresultId":"mocked-testcase1-2",
          |"testresultDatetimeRun":"2016-02-20 22:07:12+0000",
          |"runtimeMilliseconds":987,
          |"numberOf200":123,
          |"numberOf400":456,
          |"numberOf500":789}
          |]
          |""".stripMargin.replace("\n", "")
      terminateActorSystem()
    }

    "return a JSON object with an error message if there is a problem with the repository" in new AkkaTestkitSupport {
      val Some(response) = route(app, FakeRequest(GET, "/testcases/testcaseWithFailure/statistics/latest/"))

      status(response) mustBe INTERNAL_SERVER_ERROR
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe """{"message":"An error occured: Error in mock"}"""
      terminateActorSystem()
    }

    "return an empty JSON array if no statistics for a given testcase id exist" in new AkkaTestkitSupport {
      val Some(response) = route(app, FakeRequest(GET, "/testcases/testcaseWithoutStatistics/statistics/latest/"))

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe "[]"
      terminateActorSystem()
    }

    "return a JSON object with an error message if the minTestresultDatetimeRun string is incorrectly formatted" in new AkkaTestkitSupport {
      val Some(response) = route(app, FakeRequest(GET, "/testcases/testcaseWithFailure/statistics/latest/?minTestresultDatetimeRun=foo"))

      status(response) mustBe BAD_REQUEST
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe """{"message":"Invalid minTestresultDatetimeRun format. You provided 'foo', use yyyy-MM-dd HH:mm:ssZ (e.g. 2016-01-02 03:04:05+0600)"}"""
      terminateActorSystem()
    }

    "return a JSON object with an error message if the minTestresultDatetimeRun string is empty" in new AkkaTestkitSupport {
      val Some(response) = route(app, FakeRequest(GET, "/testcases/testcaseWithFailure/statistics/latest/?minTestresultDatetimeRun="))

      status(response) mustBe BAD_REQUEST
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe """{"message":"Invalid minTestresultDatetimeRun format. You provided '', use yyyy-MM-dd HH:mm:ssZ (e.g. 2016-01-02 03:04:05+0600)"}"""
      terminateActorSystem()
    }
  }

}
