package com.journeymonitor.analyze.common

import org.scalatest.{Matchers, FunSpec}

class CassandraConnectionUriSpec extends FunSpec with Matchers {

  describe("A Cassandra connection URI object") {
    it("should parse a URI with a single host") {
      val ccu = CassandraConnectionUri("cassandra://localhost:9042/test")
      ccu.host should be ("localhost")
      ccu.hosts should be (Seq("localhost"))
      ccu.port should be (9042)
      ccu.keyspace should be ("test")
    }
    it("should parse a URI with additional hosts") {
      val ccu = CassandraConnectionUri(
        "cassandra://localhost:9042/test" +
          "?host=otherhost.example.net" +
          "&host=yet.anotherhost.example.com")
      ccu.hosts should contain allOf ("localhost", "otherhost.example.net", "yet.anotherhost.example.com")
      ccu.port should be (9042)
      ccu.keyspace should be ("test")
    }
  }

}