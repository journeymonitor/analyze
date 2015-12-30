package com.journeymonitor.analyze.common

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder._
import org.scalatest.{Matchers, FunSpec}

class CassandraClientSpec extends FunSpec with Matchers {

  describe("Connecting and querying a Cassandra database") {
    it("should just work") {
      val uriString = sys.env.getOrElse("JOURNEYMONITOR_ANALYZE_IMPORTER_CASSANDRAURI_TEST", "cassandra://localhost:9042/test")
      val uri = CassandraConnectionUri(uriString)
      val session = CassandraClient.createSessionAndInitKeyspace(uri)

      session.execute("INSERT INTO things (id, name) VALUES (1, 'foo');")

      val selectStmt = select().column("name")
        .from("things")
        .where(QueryBuilder.eq("id", 1))
        .limit(1)

      val resultSet = session.execute(selectStmt)
      val row = resultSet.one()
      row.getString("name") should be("foo")
      session.execute("TRUNCATE things;")
    }
  }

}