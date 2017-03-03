package com.journeymonitor.analyze.spark

import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Calendar

import com.datastax.spark.connector._
import org.apache.log4j.LogManager
import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.json4s._
import org.json4s.jackson.JsonMethods._

/*

Goal 1:
  For each testcase, show a diagram with some statistics
  on the requests of the last N testruns, like overall run time,
  # of 2xx, 4xx, 5xx responses etc.:

tr1       tr2
  *       * *
* *       * *
* *       * *
* *       * * *
* * *     * * * *
t 2 4 5   t 2 4 5
i x x x   i x x x
m x x x   m x x x
e         e

*/

case class Testresult(testcaseId: String,
                      testresultId: String,
                      datetimeRun: java.util.Date,
                      har: JValue)

case class Statistics(testcaseId: String,
                      dayBucket: String,
                      testresultDatetimeRun: java.util.Date,
                      testresultId: String,
                      totalRequestTime: Int,
                      numberOfRequestsWithStatus200: Int,
                      numberOfRequestsWithStatus400: Int,
                      numberOfRequestsWithStatus500: Int)

object HarAnalyzer {
  private def calculateNumberOfRequestsWithResponseStatus(status: Int, entries: List[JsonAST.JValue]): Int = {
    implicit val formats = org.json4s.DefaultFormats
    val requestCounter = for {
      entry <- entries
      if {
        try {
          val entryStatus = (entry \ "response" \ "status").extract[Int]
          entryStatus >= status && entryStatus < status + 100
        } catch {
          case e: Exception => {
            val logger = LogManager.getLogger(this.getClass)
            logger.warn(s"Problem: '${e.getMessage}' while trying to get the status code at 'response -> status' within ${entry.toString()}")
            false
          }
        }
      }
    } yield 1
    if (requestCounter.isEmpty) 0 else requestCounter.reduce(_ + _)
    // This is a "normal" Scala reduce, not an RDD reduce.
    // Because this method is called from within testresultsRDD.map, the reduce does not happen in the driver,
    // but in the executors
  }

  private def calculateTotalRequestTime(entries: List[JsonAST.JValue]): Int = {
    implicit val formats = org.json4s.DefaultFormats

    val formatterWithMilliseconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx")
    val formatterWithoutMilliseconds = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")

    val starttimesEpochMilli = for { entry <- entries } yield {
      val startedDateTime = (entry \ "startedDateTime").extract[String]
      try {
        java.time.ZonedDateTime.parse(startedDateTime, formatterWithMilliseconds).toInstant.toEpochMilli
      } catch {
        case e: java.time.format.DateTimeParseException =>
          java.time.ZonedDateTime.parse(startedDateTime, formatterWithoutMilliseconds).toInstant.toEpochMilli
      }
    }

    val endtimesEpochMilli = for { entry <- entries } yield {
      val startedDateTime = (entry \ "startedDateTime").extract[String]
      val time = (entry \ "time").extract[Int]
      try {
        java.time.ZonedDateTime.parse(startedDateTime, formatterWithMilliseconds).toInstant.toEpochMilli + time
      } catch {
        case e: java.time.format.DateTimeParseException =>
          java.time.ZonedDateTime.parse(startedDateTime, formatterWithoutMilliseconds).toInstant.toEpochMilli + time
      }
    }

    (endtimesEpochMilli.max - starttimesEpochMilli.min).toInt
  }

  def calculateRequestStatistics(testresultsRDD: RDD[Testresult]): RDD[Statistics] = {
    def yMd(calendar: Calendar): String = {
      val sdf = new SimpleDateFormat("yyyy-MM-dd")
      sdf.format(calendar.getTime)
    }
    testresultsRDD.map(testresult => {
      val logger = LogManager.getLogger(this.getClass)
      logger.info(s"Starting to extract statistics from testresult ${testresult.testresultId}...")
      val entries = (testresult.har \ "log" \ "entries").children
      val cal = java.util.Calendar.getInstance()
      cal.setTime(testresult.datetimeRun)
      val statistics = Statistics(
        testcaseId = testresult.testcaseId,
        dayBucket = yMd(cal),
        testresultDatetimeRun = testresult.datetimeRun,
        testresultId = testresult.testresultId,
        totalRequestTime = calculateTotalRequestTime(entries),
        numberOfRequestsWithStatus200 = calculateNumberOfRequestsWithResponseStatus(200, entries),
        numberOfRequestsWithStatus400 = calculateNumberOfRequestsWithResponseStatus(400, entries),
        numberOfRequestsWithStatus500 = calculateNumberOfRequestsWithResponseStatus(500, entries)
      )
      logger.info(s"Finished extracting statistics from testresult ${testresult.testresultId}...")
      statistics
    })
  }
}

object SparkApp {
  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("JourneyMonitor Analyze")
    val cassandraHost = sys.env.getOrElse("JOURNEYMONITOR_ANALYZE_SPARK_CASSANDRAHOST", "127.0.0.1")
    conf.set("spark.cassandra.connection.host", cassandraHost)
    val sc = new SparkContext(conf)

    val rowsRDD = sc.cassandraTable("analyze", "testresults")

    // Create RDD with a tuple of Testcase ID, Testresult ID, DateTime of Run, HAR per entry
    // Not calling .cache() because that results in OOM
    // Note: this flatMap is where executors spend the most time (currently around 2s for ~330 rows (~42 MB))
    // The suspect here is the parse operation because our JSON is quite large and complex
    val testresultsRDD =
      rowsRDD.flatMap(
        row => {
          try { // filter out entries with malformed data
            Seq(
              Testresult(
                testcaseId = row.get[String]("testcase_id"),
                testresultId = row.get[String]("testresult_id"),
                datetimeRun = row.get[java.util.Date]("datetime_run"),
                har = parse(row.get[String]("har"), false)
              ))
          } catch {
            case e: Exception => Seq()
          }
        }
      )

    val statisticsRDD = HarAnalyzer.calculateRequestStatistics(testresultsRDD)

    statisticsRDD.saveToCassandra(
      "analyze",
      "statistics",
      SomeColumns(
        "testcase_id"             as "testcaseId",
        "day_bucket"              as "dayBucket",
        "testresult_id"           as "testresultId",
        "testresult_datetime_run" as "testresultDatetimeRun",
        "runtime_milliseconds"    as "totalRequestTime",
        "number_of_200"           as "numberOfRequestsWithStatus200",
        "number_of_400"           as "numberOfRequestsWithStatus400",
        "number_of_500"           as "numberOfRequestsWithStatus500"
      )
    )

    val cassandraConnector = rowsRDD.connector
    rowsRDD.foreachPartition { partition =>
      val session = cassandraConnector.openSession
      partition.foreach {
        row => session.execute("DELETE FROM analyze.testresults WHERE testresult_id = '" + row.getString("testresult_id") + "';")
      }
    }

    sc.stop()

  }
}
