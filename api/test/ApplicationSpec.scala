import java.io.File

import com.datastax.driver.core.querybuilder.QueryBuilder._
import com.journeymonitor.analyze.common.{CassandraClient, CassandraConnectionUri}
import org.scassandra.server.ServerStubRunner
import scala.collection.JavaConversions._ // required to make Map[String, Any] work
import models.StatisticsModel
import org.scalatestplus.play._
import org.scassandra.http.client.PrimingRequest
import org.scassandra.http.client.PrimingRequest.then
import org.scassandra.http.client.types.ColumnMetadata.column
import org.scassandra.junit.ScassandraServerRule
import org.scassandra.cql.PrimitiveType._
import play.api
import play.api.ApplicationLoader.Context
import play.api.test.Helpers._
import play.api.test._
import play.api.{ApplicationLoader, Environment, Mode}
import repositories.Repository
import scala.util.Try

class MockStatisticsRepository extends Repository[StatisticsModel, String] {
  override def getNById(id: String, n: Int): Try[List[StatisticsModel]] = {
    Try {
      id match {
        case "testcaseWithFailure" => throw new Exception("blubb")
        case id => List(
          StatisticsModel("mocked-" + id, 987, 123, 456, 789)
        )
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
      val Some(response) = route(FakeRequest(GET, "/testcases/testcase1/statistics/latest/?n=1"))

      status(response) mustBe OK
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe
        """
          |[{"testresultId":"mocked-testcase1",
          |"runtimeMilliseconds":987,
          |"numberOf200":123,
          |"numberOf400":456,
          |"numberOf500":789}]
          |""".stripMargin.replace("\n", "")
    }

    "return a JSON object with an error message if there is a problem with the repository" in {
      val Some(response) = route(FakeRequest(GET, "/testcases/testcaseWithFailure/statistics/latest/?n=1"))

      status(response) mustBe INTERNAL_SERVER_ERROR
      contentType(response) mustBe Some("application/json")
      charset(response) mustBe Some("utf-8")
      contentAsString(response) mustBe """{"message":"An error occured"}"""
    }
  }

  "Querying scassandra" should {

    "work" in {
      val s = new ServerStubRunner()
      s.start()
      Thread.sleep(1000L)

      val sc = new ScassandraServerRule()
      val pc = sc.primingClient()
      val ac = sc.activityClient()

      val row = Map[String, Any]("name" -> "John Doe", "age" -> 42)

      pc.prime(PrimingRequest.queryBuilder()
        .withQuery("""SELECT name,age FROM foo;""")
          .withThen(then()
            .withColumnTypes(column("name", TEXT), column("age", INT))
            .withRows(row)
        )
        .build()
      )

      val uri = CassandraConnectionUri("cassandra://localhost:8042/scassandra")
      val session = CassandraClient.createSessionAndInitKeyspace(uri)

      val selectStmt = select()
        .column("name")
        .column("age")
        .from("foo")

      val resultSet = session.execute(selectStmt)
      val resultRow = resultSet.one()
      resultRow.getString("name") must be("John Doe")
      resultRow.getInt("age") must be(42)
    }

  }

}
