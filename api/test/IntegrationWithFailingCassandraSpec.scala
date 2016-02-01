import java.io.File

import com.journeymonitor.analyze.common.{CassandraClient, CassandraConnectionUri}
import org.scalatest.BeforeAndAfter
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

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery("""SELECT * FROM statistics WHERE testcase_id=? LIMIT 2;""")
      .withThen(then().withResult(PrimingRequest.Result.read_request_timeout))
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

    "return an error upon encountering a Cassandra ReadTimeout" in {
      go to "http://localhost:" + port + "/testcases/testcaseFoo/statistics/latest/?n=2"
      pageSource mustBe """{"message":"An error occured"}"""
    }

  }
}
