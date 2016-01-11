import com.journeymonitor.analyze.common.{CassandraClient, CassandraConnectionUri}

object Common {
  def main(args: Array[String]) {

    val uriStringTest = sys.env.getOrElse("JOURNEYMONITOR_ANALYZE_CASSANDRAURI_TEST", "cassandra://localhost:9042/test")
    val uriTest = CassandraConnectionUri(uriStringTest)
    println(s"Applying test Pillar migrations by connecting to $uriStringTest...")
    val sessionTest = CassandraClient.createSessionAndInitKeyspace(uriTest)
    println(s"Finished applying test Pillar migrations by connecting to $uriStringTest")
    sessionTest.close()
    sessionTest.getCluster().close()

    val uriStringProd = sys.env.getOrElse("JOURNEYMONITOR_ANALYZE_CASSANDRAURI", "cassandra://localhost:9042/analyze")
    val uriProd = CassandraConnectionUri(uriStringProd)
    println(s"Applying prod Pillar migrations by connecting to $uriStringProd...")
    val sessionProd = CassandraClient.createSessionAndInitKeyspace(uriProd)
    println(s"Finished applying prod Pillar migrations by connecting to $uriStringProd")
    sessionProd.close()
    sessionProd.getCluster().close()
  }
}
