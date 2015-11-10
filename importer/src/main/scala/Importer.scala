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
 */

object Importer {
  def main(args: Array[String]) {

    implicit val formats = DefaultFormats

    val uri = CassandraConnectionUri("cassandra://localhost:9042/source_data")
    val session = CassandraClient.createSessionAndInitKeyspace(uri)

    val stmt = session.prepare(
      "INSERT INTO testresults " +
      "(testcase_id, datetime_run, testresult_id, har, is_analyzed) " +
      "VALUES (?, ?, ?, ?, ?);")

    val filename = args(0)
    val jsonString = try {
      scala.io.Source.fromFile(filename).mkString
    } catch {
      case e: Exception => {
        println(e)
        ""
      }
    }

    if (jsonString == "") {
      session.close()
      session.getCluster.close()
      System.exit(1)
    }

    val json = parse(jsonString)
    val entries = json.children
    entries.foreach(entry => {
      val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
      val datetimeRun = format.parse((entry \ "datetimeRun").extract[String])
      val boundStmt = stmt.bind(
        (entry \ "testcaseId").extract[String],
        datetimeRun,
        (entry \ "id").extract[String],
        (entry \ "har").extract[String]
        // simply passing `false` here results in "the result type of an implicit conversion must be more specific than AnyRef"
      )
      boundStmt.setBool(4, false)
      session.execute(boundStmt)
      println("Added Testresult " + (entry \ "id").extract[String] + " for Testcase " + (entry \ "testcaseId").extract[String])
    })

    session.close()
    session.getCluster.close()
  }
}
