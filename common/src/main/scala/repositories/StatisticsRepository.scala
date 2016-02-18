package com.journeymonitor.analyze.common.repositories

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder._
import com.datastax.driver.core.{ResultSet, Row, Session}
import com.journeymonitor.analyze.common.models.StatisticsModel

trait StatisticsRepository {
  def getAllForTestcaseIdYoungerThanOrEqualTo(testcaseId: String, dateTime: java.util.Date): List[StatisticsModel]
}

class StatisticsCassandraRepository(session: Session)
  extends CassandraRepository[StatisticsModel, String](session, "statistics", "testcase_id")
  with StatisticsRepository {
  override def rowToModel(row: Row): StatisticsModel = {
    StatisticsModel(
      row.getString("testresult_id"),
      row.getDate("testresult_datetime_run"),
      row.getInt("runtime_milliseconds"),
      row.getInt("number_of_200"),
      row.getInt("number_of_400"),
      row.getInt("number_of_500"))
  }

  def getAllForTestcaseIdYoungerThanOrEqualTo(testcaseId: String, dateTime: java.util.Date): Seq[StatisticsModel] = {
    import scala.collection.JavaConversions._
    /* TODO:
        - is the dateTime from today? Then we only need to look in the current day_bucket
        - is the dateTime from a day earlier than today? Then we need to start at the matching
          day_bucket and work our way up
        - For each day_bucket y:
         - SELECT * FROM statistics WHERE testcase_id = x and day_bucket = y and testresult_datetime_run >= dateTime
           // returns from oldest to youngest
         - From here on return all rows plus all rows from higher day_buckets, if any
     */
    val dayBuckets = Seq("2016-02-17", "2016-02-18")
    val rows = (for (dayBucket <- dayBuckets)
      yield session.execute(
        select()
          .from(tablename)
          .where(QueryBuilder.eq("testcase_id", testcaseId))
          .and(QueryBuilder.eq("day_bucket", "2015-05-04"))
          .and(QueryBuilder.gte("testresult_datetime_run", dateTime))
      ).all().toList).flatten
    rows.map(row => rowToModel(row))
  }
}
