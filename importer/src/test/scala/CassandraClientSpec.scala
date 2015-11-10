package com.journeymonitor.analyze.importer

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder._
import org.scalatest.{Matchers, FunSpec}

class CassandraClientSpec extends FunSpec with Matchers {

  describe("Connecting and querying a Cassandra database") {
    it("should just work") {
      val uri = CassandraConnectionUri("cassandra://localhost:9042/test")
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