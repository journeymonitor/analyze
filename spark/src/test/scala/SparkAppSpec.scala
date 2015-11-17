package com.journeymonitor.analyze.sparkapp

import java.text.SimpleDateFormat

import org.apache.spark.SparkConf
import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.json4s
import org.json4s.native.JsonMethods._
import org.scalatest.{BeforeAndAfter, FunSpec, Matchers}

case class Testresult(testcaseId: String, testresultId: String, datetimeRun: java.util.Date, har: json4s.JValue)
case class Statistics(numberOfRequestsWithStatus200: Int, numberOfRequestsWithStatus400: Int)

object FixtureGenerator {
  def getTestresultsRDD(sc: SparkContext) = {
    val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    val datetimeRun = format.parse("2015-11-17 12:34:56")
    sc.parallelize(Seq(
      Testresult(
        testcaseId = "testcaseId1",
        testresultId = "testresultId1",
        datetimeRun = datetimeRun,
        har = parse(
          """
            |{
            |  "log": {
            |    "entries": [
            |      {
            |        "response": {
            |          "status": 200
            |        }
            |      },
            |      {
            |        "response": {
            |          "status": 400
            |        }
            |      }
            |    ]
            |  }
            |}
          """.stripMargin, false)
      ),
      Testresult(
        testcaseId = "testcaseId1",
        testresultId = "testresultId2",
        datetimeRun = datetimeRun,
        har = parse(
          """
{
            |  "log": {
            |    "entries": [
            |      {
            |        "response": {
            |          "status": 400
            |        }
            |      },
            |      {
            |        "response": {
            |          "status": 400
            |        }
            |      }
            |    ]
            |  }
            |}
          """.stripMargin, false)

      )
    ))
  }
}

object HarAnalyzer {
  private def calculateNumberOfRequestsWithResponseStatus(status: Int, testresult: Testresult): Int = {
    implicit val formats = org.json4s.DefaultFormats
    val entries = (testresult.har \ "log" \ "entries").children
    val requestCounter = for {
      entry <- entries
      if ((entry \ "response" \ "status").extract[Int] >= status && (entry \ "response" \ "status").extract[Int] < status + 100)
    } yield 1
    if (requestCounter.isEmpty) 0 else requestCounter.reduce(_ + _)
  }

  def calculateNumberOfRequests(testresultsRDD: RDD[Testresult]): RDD[Statistics] = {
    testresultsRDD.map(testresult => {
      Statistics(
        numberOfRequestsWithStatus200 = calculateNumberOfRequestsWithResponseStatus(200, testresult),
        numberOfRequestsWithStatus400 = calculateNumberOfRequestsWithResponseStatus(400, testresult)
      )
    })
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
    it("should calculate requests based on reponse status code") {
      val testresultsRDD = FixtureGenerator.getTestresultsRDD(sc)
      val statisticsRDD = HarAnalyzer.calculateNumberOfRequests(testresultsRDD)
      val statistics = statisticsRDD.collect()

      statistics.apply(0).numberOfRequestsWithStatus200 should be(1)
      statistics.apply(0).numberOfRequestsWithStatus400 should be(1)

      statistics.apply(1).numberOfRequestsWithStatus200 should be(0)
      statistics.apply(1).numberOfRequestsWithStatus400 should be(2)
    }
  }

}
