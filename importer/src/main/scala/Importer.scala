package com.journeymonitor.analyze.importer

import java.text.SimpleDateFormat

import org.json4s.DefaultFormats
import org.json4s.native.JsonMethods._

import scala.io.BufferedSource

/*
  Format of one entry in JSON array as produced by MONITOR:
  {
    "id":"647B2818-6401-425E-9EF0-7455E08F7479",
    "testcaseId":"657D6D9E-7D59-472A-BD16-B291CC4573DC",
    "datetimeRun":"2015-11-10 12:25:08",
    "exitCode":"0",
    "output":"",
    "failScreenshotFilename":null|"",
    "har":""
   }

   Each entry is on one single line of its own, and all entries are part of one large array:

   [
    {...},
    {...},
    {...},
    {...}
   ]

 */

object Importer {
  def main(args: Array[String]) {

    implicit val formats = DefaultFormats

    val uri = CassandraConnectionUri("cassandra://localhost:9042/analyze")
    val session = CassandraClient.createSessionAndInitKeyspace(uri)

    val stmt = session.prepare(
      "INSERT INTO testresults " +
      "(testcase_id, datetime_run, testresult_id, har, is_analyzed) " +
      "VALUES (?, ?, ?, ?, ?);")

    val filename = if (args.length == 0) "" else (args(0))

    try {
      scala.io.Source.fromFile(filename).getLines().foreach(line => {
        if (!line.startsWith("[") && !line.startsWith("]")) {
          val normalizedLine = if (line.endsWith(",")) { // last line will not end with a comma
            line.take(line.length - 1)
          } else {
            line
          }
          val entryJson = parse(normalizedLine)
          val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
          val datetimeRun = format.parse((entryJson \ "datetimeRun").extract[String])
          val boundStmt = stmt.bind(
            (entryJson \ "testcaseId").extract[String],
            datetimeRun,
            (entryJson \ "id").extract[String],
            (entryJson \ "har").extract[String]
            // simply passing `false` here results in "the result type of an implicit conversion must be more specific than AnyRef"
          )
          boundStmt.setBool(4, false)
          try {
            session.execute(boundStmt)
            println("Added Testresult " + (entryJson \ "id").extract[String] + " from " + datetimeRun + " for Testcase " + (entryJson \ "testcaseId").extract[String])
          } catch {
            case e: Exception => {
              println("Could not add testresult " + (entryJson \ "id").extract[String] + " from " + datetimeRun + " for Testcase " + (entryJson \ "testcaseId").extract[String])
              println(e)
            }
          }
        }
      })
    } catch {
      case e: Exception => {
        println(e)
        session.close()
        session.getCluster.close()
        System.exit(1)
      }
    }

    session.close()
    session.getCluster.close()
  }
}
