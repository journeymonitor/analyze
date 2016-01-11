package com.journeymonitor.analyze.importer

import java.text.SimpleDateFormat

import org.json4s._
import org.json4s.jackson.JsonMethods._
import scala.io.BufferedSource
import com.journeymonitor.analyze.common.{CassandraConnectionUri,CassandraClient}

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
  def printMem() {
    val mb = 1024*1024
    val runtime = Runtime.getRuntime
    println("** Used Memory:  " + (runtime.totalMemory - runtime.freeMemory) / mb)
    println("** Free Memory:  " + runtime.freeMemory / mb)
    println("** Total Memory: " + runtime.totalMemory / mb)
    println("** Max Memory:   " + runtime.maxMemory / mb)
  }

  def cleanupAndAbort(e: Exception, session: com.datastax.driver.core.Session) {
    println(e)
    session.close()
    session.getCluster().close()
    System.exit(1)
  }

  def run (filename: String, session: com.datastax.driver.core.Session) {
    implicit val formats = DefaultFormats
    val stmt = session.prepare(
      "INSERT INTO testresults " +
        "(testcase_id, datetime_run, testresult_id, har, is_analyzed) " +
        "VALUES (?, ?, ?, ?, ?);")

    try {
      val source = scala.io.Source.fromFile(filename)
      try {
        source.getLines().foreach(line => {
          if (!line.startsWith("[") && !line.startsWith("]")) {
            val normalizedLine = if (line.endsWith(",")) {
              // last line will not end with a comma
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
          printMem()
        })
      } catch { // We could access the file, but encountered a problem while doing so
        case e: Exception => {
          cleanupAndAbort(e, session)
        }
      } finally {
        source.close()
      }
    } catch { // The provided file cannot be accesses (not found, insufficient permission, etc.)
      case e: Exception => {
        cleanupAndAbort(e, session)
      }
    }
    session.close()
    session.getCluster.close()
  }

  def main(args: Array[String]) {
    val filename = if (args.length == 0) "" else (args(0))

    val uriString = sys.env.getOrElse("JOURNEYMONITOR_ANALYZE_CASSANDRAURI", "cassandra://localhost:9042/analyze")
    val cassandraConnectionUri = CassandraConnectionUri(uriString)

    val sessionOption: Option[com.datastax.driver.core.Session] = try {
      Some(CassandraClient.createSessionAndInitKeyspace(cassandraConnectionUri))
    } catch {
      case e: Exception => {
        println(e)
        None
      }
    }
    sessionOption match {
      case Some(session) => run(filename, session)
      case None => System.exit(1)
    }
  }

}
