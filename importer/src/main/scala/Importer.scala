package com.journeymonitor.analyze.importer

import org.json4s.native.JsonMethods._

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
    val file = "/Users/manuelkiessling/Dropbox/Projects/selenior/analyze/testresults.json"
    val jsonString = scala.io.Source.fromFile(file).mkString
    val json = parse(jsonString)
    val entries = json.children
    entries.foreach(entry => {
      println(entry \ "id")
    })
  }
}
