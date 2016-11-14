package com.journeymonitor.analyze.api.test

import java.io.File
import java.util.Calendar

import com.journeymonitor.analyze.api.AppComponents
import com.journeymonitor.analyze.common.util.Util
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

  // TODO: Problematic if test case runs at 23:59:59...
  val yesterday = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
  yesterday.add(Calendar.DATE, -1)
  val yesterdayDaybucket = Util.yMd(yesterday)
  val yesterdayTimestamp = yesterday.getTime.getTime / 1000 * 1000
  val yesterdayDatetime = Util.fullDatetimeWithRfc822Tz(yesterday)
  val yesterdayDatetimeUrlEncoded = java.net.URLEncoder.encode(yesterdayDatetime, "utf-8")

  class FakeApplicationComponents(context: Context) extends AppComponents(context) {
    val s = new ServerStubRunner()
    s.start()
    s.awaitStartup()

    val pc = new ScassandraServerRule().primingClient()

    import scala.collection.JavaConversions._ // required to map from Scala 'Any' to Java '? extends Object>'
    val row = Map[String, Any](
      "testresult_id" -> "foo",
      "testresult_datetime_run" -> yesterdayTimestamp,
      "runtime_milliseconds" -> 42,
      "number_of_200" -> 23,
      "number_of_400" -> 4,
      "number_of_500" -> 5
    )

    val result = then()
      .withColumnTypes(
        column("testresult_id", TEXT),
        column("testresult_datetime_run", TIMESTAMP),
        column("runtime_milliseconds", INT),
        column("number_of_200", INT),
        column("number_of_400", INT),
        column("number_of_500", INT)
      )
      .withRows(row)

    val query3readtimeouts = s"SELECT * FROM statistics " +
      s"WHERE testcase_id='testcase3readtimeouts' " +
      s"AND day_bucket='$yesterdayDaybucket' " +
      s"AND testresult_datetime_run>=$yesterdayTimestamp;"
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

    val query2readtimeouts = s"SELECT * FROM statistics " +
      s"WHERE testcase_id='testcase2readtimeouts' " +
      s"AND day_bucket='$yesterdayDaybucket' " +
      s"AND testresult_datetime_run>=$yesterdayTimestamp;"

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

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery(query2readtimeouts + " /* 3. try */")
      .withThen(result)
      .build()
    )

    val query3unavailable = s"SELECT * FROM statistics " +
      s"WHERE testcase_id='testcase3unavailable' " +
      s"AND day_bucket='$yesterdayDaybucket' " +
      s"AND testresult_datetime_run>=$yesterdayTimestamp;"
    val unavailable = then().withResult(PrimingRequest.Result.unavailable)

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery(query3unavailable + " /* 1. try */")
      .withThen(unavailable)
      .build()
    )

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery(query3unavailable + " /* 2. try */")
      .withThen(unavailable)
      .build()
    )

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery(query3unavailable + " /* 3. try */")
      .withThen(unavailable)
      .build()
    )

    val query2unavailable = s"SELECT * FROM statistics " +
      s"WHERE testcase_id='testcase2unavailable' " +
      s"AND day_bucket='$yesterdayDaybucket' " +
      s"AND testresult_datetime_run>=$yesterdayTimestamp;"

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery(query2unavailable + " /* 1. try */")
      .withThen(unavailable)
      .build()
    )

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery(query2unavailable + " /* 2. try */")
      .withThen(unavailable)
      .build()
    )

    pc.prime(PrimingRequest.queryBuilder()
      .withQuery(query2unavailable + " /* 3. try */")
      .withThen(result)
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
      go to "http://localhost:" +
        port +
        s"/testcases/testcase3readtimeouts/statistics/latest/?minTestresultDatetimeRun=$yesterdayDatetimeUrlEncoded"
      pageSource mustBe """{"message":"An error occured: Database read timeout"}"""
    }

    "return a result upon encountering only 2 Cassandra read timeouts in a row followed by a success" in {
        go to "http://localhost:" +
          port +
          s"/testcases/testcase2readtimeouts/statistics/latest/?minTestresultDatetimeRun=$yesterdayDatetimeUrlEncoded"
        pageSource mustBe
          s"""
            |[{"testresultId":"foo",
            |"testresultDatetimeRun":"$yesterdayDatetime",
            |"runtimeMilliseconds":42,
            |"numberOf200":23,
            |"numberOf400":4,
            |"numberOf500":5}]
            |""".stripMargin.replace("\n", "")
      }

    "return an error upon encountering 3x 'Cassandra unavailable' in a row" in {
      go to "http://localhost:" +
        port +
        s"/testcases/testcase3unavailable/statistics/latest/?minTestresultDatetimeRun=$yesterdayDatetimeUrlEncoded"
      pageSource mustBe """{"message":"An error occured: Not enough database nodes available"}"""
    }

    "return a result upon encountering only 2x 'Cassandra unavailable' in a row followed by a success" in {
      go to "http://localhost:" +
        port +
        s"/testcases/testcase2unavailable/statistics/latest/?minTestresultDatetimeRun=$yesterdayDatetimeUrlEncoded"
      pageSource mustBe
        s"""
          |[{"testresultId":"foo",
          |"testresultDatetimeRun":"$yesterdayDatetime",
          |"runtimeMilliseconds":42,
          |"numberOf200":23,
          |"numberOf400":4,
          |"numberOf500":5}]
          |""".stripMargin.replace("\n", "")
    }
  }
}
