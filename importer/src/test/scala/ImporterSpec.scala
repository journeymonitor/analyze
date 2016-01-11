package com.journeymonitor.analyze.importer

import com.datastax.driver.core.querybuilder.QueryBuilder._
import com.journeymonitor.analyze.common.{CassandraClient, CassandraConnectionUri}
import org.scalatest.{Matchers, FunSpec}

class ImporterSpec extends FunSpec with Matchers {

  describe("Importing testresults") {
    it("should work if the file is valid") {
      val uriString = sys.env.getOrElse(
        "JOURNEYMONITOR_ANALYZE_CASSANDRAURI_TEST",
        "cassandra://localhost:9042/test"
      )
      val uri = CassandraConnectionUri(uriString)
      val firstSession = CassandraClient.createSessionAndInitKeyspace(uri)

      firstSession.execute("TRUNCATE testresults;")

      Importer.run("./testresults.json", firstSession)

      val secondSession = CassandraClient.createSessionAndInitKeyspace(uri)

      val selectStmt = select().column("testresult_id")
        .from("testresults")
        .limit(1)

      val resultSet = secondSession.execute(selectStmt)
      val row = resultSet.one()
      row.getString("testresult_id") should be("61C19634-9B83-4E63-8EBD-7D7469AB678C")
    }
  }

}
