package com.journeymonitor.analyze.spark

import java.text.SimpleDateFormat

import org.apache.spark.{SparkConf, _}
import org.json4s._
import org.json4s.jackson.JsonMethods._
import org.scalatest.{BeforeAndAfter, FunSpec, Matchers}

object FixtureGenerator {
  def getTestresultsRDD(sc: SparkContext) = {
    val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val datetimeRun1 = format.parse("2015-01-02 23:59:59")
    val datetimeRun2 = format.parse("2015-11-18 00:00:00")
    sc.parallelize(Seq(
      Testresult(
        testcaseId = "testcaseId1",
        testresultId = "testresultId1",
        datetimeRun = datetimeRun1,
        har = parse(
          """
            |{
            |  "log": {
            |    "entries": [
            |      {
            |        "response": {
            |          "status": 200
            |        },
            |        "time": 10
            |      },
            |      {
            |        "response": {
            |          "status": 400
            |        },
            |        "time": 15
            |      }
            |    ]
            |  }
            |}
          """.stripMargin, false)
      ),
      Testresult(
        testcaseId = "testcaseId1",
        testresultId = "testresultId2",
        datetimeRun = datetimeRun2,
        har = parse(
          """
{
            |  "log": {
            |    "entries": [
            |      {
            |        "response": {
            |          "status": 400
            |        },
            |        "time": 16
            |      },
            |      {
            |        "response": {
            |          "status": 400
            |        },
            |        "time": 4
            |      }
            |    ]
            |  }
            |}
          """.stripMargin, false)

      )
    ))
  }
}

class SparkExampleSpec extends FunSpec with BeforeAndAfter with Matchers {

  private val master = "local[2]"
  private val appName = "example-spark"

  private var sc: SparkContext = _

  before {
    val conf = new SparkConf()
      .setMaster(master)
      .setAppName(appName)

    sc = new SparkContext(conf)
  }

  after {
    if (sc != null) {
      sc.stop()
    }
  }

  describe("The HarAnalyzer") {
    it("should extract statistics from HARs") {
      val testresultsRDD = FixtureGenerator.getTestresultsRDD(sc)
      val statisticsRDD = HarAnalyzer.calculateRequestStatistics(testresultsRDD)
      val statistics = statisticsRDD.collect()

      statistics(0).testcaseId should be("testcaseId1")
      statistics(0).dayBucket should be("2015-01-02")
      statistics(0).testresultId should be("testresultId1")
      statistics(0).testresultDatetimeRun.toString.substring(0, 20) should be("Fri Jan 02 23:59:59 ") // @TODO: Stupid hack because we do not yet store the timezone
      statistics(0).testresultDatetimeRun.toString.substring(24) should be("2015")
      statistics(0).numberOfRequestsWithStatus200 should be(1)
      statistics(0).numberOfRequestsWithStatus400 should be(1)
      statistics(0).totalRequestTime should be(25)

      statistics(1).testcaseId should be("testcaseId1")
      statistics(1).dayBucket should be("2015-11-18")
      statistics(1).testresultId should be("testresultId2")
      statistics(1).testresultDatetimeRun.toString.substring(0, 20) should be("Wed Nov 18 00:00:00 ")
      statistics(1).testresultDatetimeRun.toString.substring(24) should be("2015")
      statistics(1).numberOfRequestsWithStatus200 should be(0)
      statistics(1).numberOfRequestsWithStatus400 should be(2)
      statistics(1).totalRequestTime should be(20)
    }
  }

}
