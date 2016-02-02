import java.io.File

import com.journeymonitor.analyze.common.{CassandraClient, CassandraConnectionUri}
import org.scalatestplus.play._
import org.scassandra.cql.PrimitiveType._
import org.scassandra.http.client.PrimingRequest
import org.scassandra.http.client.PrimingRequest._
import org.scassandra.http.client.types.ColumnMetadata._
import org.scassandra.junit.ScassandraServerRule
import org.scassandra.server.ServerStubRunner
import play.api
import play.api.ApplicationLoader.Context
import play.api.{ApplicationLoader, Environment, Mode}

class IntegrationWithFailingCassandraSpec extends PlaySpec with OneBrowserPerSuite with OneServerPerSuite with HtmlUnitFactory {

  class FakeApplicationComponents(context: Context) extends AppComponents(context) {
    val s = new ServerStubRunner()
    s.start()
    s.awaitStartup()

    val pc = new ScassandraServerRule().primingClient()

    val query3readtimeouts = "SELECT * FROM statistics WHERE testcase_id='testcase3readtimeouts' LIMIT 2;"
    val timeout = then().withResult(PrimingRequest.Result.read_request_timeout)

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery(query3readtimeouts + " /* 1. try */")
      .withThen(timeout)
      .build()
    )

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery(query3readtimeouts + " /* 2. try */")
      .withThen(timeout)
      .build()
    )

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery(query3readtimeouts + " /* 3. try */")
      .withThen(timeout)
      .build()
    )

    val query2readtimeouts = "SELECT * FROM statistics WHERE testcase_id='testcase2readtimeouts' LIMIT 2;"

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery(query2readtimeouts + " /* 1. try */")
      .withThen(timeout)
      .build()
    )

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery(query2readtimeouts + " /* 2. try */")
      .withThen(timeout)
      .build()
    )

    import scala.collection.JavaConversions._ // required to map from Scala 'Any' to Java '? extends Object>'
    val row = Map[String, Any](
      "testresult_id" -> "testresultFoo",
      "runtime_milliseconds" -> 42,
      "number_of_200" -> 23,
      "number_of_400" -> 4,
      "number_of_500" -> 5
    )

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery(query2readtimeouts + " /* 3. try */")
      .withThen(then()
        .withColumnTypes(
          column("testresult_id", TEXT),
          column("runtime_milliseconds", INT),
          column("number_of_200", INT),
          column("number_of_400", INT),
          column("number_of_500", INT)
        )
        .withRows(row)
      )
      .build()
    )

    val uri = CassandraConnectionUri("cassandra://localhost:8042/scassandra")
    val session = CassandraClient.createSessionAndInitKeyspace(uri)

    override def cassandraSession = session
  }

  class FakeAppLoader extends ApplicationLoader {
    override def load(context: Context): api.Application =
      new FakeApplicationComponents(context).application
  }

  override implicit lazy val app: api.Application = {
    val appLoader = new FakeAppLoader
    appLoader.load(
      ApplicationLoader.createContext(
        new Environment(
          new File("."), ApplicationLoader.getClass.getClassLoader, Mode.Test)
      )
    )
  }

  "Integrated application with failing Cassandra" should {

    "return an error upon encountering 3 Cassandra read timeouts in a row" in {
      go to "http://localhost:" + port + "/testcases/testcase3readtimeouts/statistics/latest/?n=2"
      pageSource mustBe """{"message":"An error occured"}"""
    }

    "return a result upon encountering only 2 Cassandra read timeouts in a row followed by a success" in {
      go to "http://localhost:" + port + "/testcases/testcase2readtimeouts/statistics/latest/?n=2"
      pageSource mustBe
        """
          |[{"testresultId":"testresultFoo",
          |"runtimeMilliseconds":42,
          |"numberOf200":23,
          |"numberOf400":4,
          |"numberOf500":5}]
          |""".stripMargin.replace("\n", "")
    }

  }
}
