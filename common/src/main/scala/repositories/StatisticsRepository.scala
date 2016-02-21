package com.journeymonitor.analyze.common.repositories

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder._
import com.datastax.driver.core.{ResultSet, Row, Session}
import com.journeymonitor.analyze.common.models.StatisticsModel

import scala.util.Try

trait ModelIterator[T] {
  def next(): Try[T]
}

trait StatisticsRepository {
  /** Returns a ModelIterator over all statistics entries since the given date and time
    *
    * The dateTime is inclusive, i.e., the iterator will return models for all rows where the
    * value of the testresult_datetime_run column is identical to or younger than the given datetime.
    */
  def getAllForTestcaseIdSinceDatetime(testcaseId: String, datetime: java.util.Date): ModelIterator
}

class StatisticsCassandraRepository(session: Session)
  extends CassandraRepository[StatisticsModel, String](session, "statistics", "testcase_id")
  with StatisticsRepository {

  class StatisticsModelIterator(resultSets: Seq[ResultSet]) extends ModelIterator[StatisticsModel] {
    def next(): Try[StatisticsModel] = {
      Try {
        val resultSet = resultSets.find(!_.isExhausted)
        resultSet match {
          case Some(r: ResultSet) => rowToModel(r.one())
          case None => throw new Exception("All ResultSets are exhausted")
        }
      }
    }
  }

  override def rowToModel(row: Row): StatisticsModel = {
    StatisticsModel(
      row.getString("testresult_id"),
      row.getDate("testresult_datetime_run"),
      row.getInt("runtime_milliseconds"),
      row.getInt("number_of_200"),
      row.getInt("number_of_400"),
      row.getInt("number_of_500"))
  }

  def getAllForTestcaseIdSinceDatetime(testcaseId: String, datetime: java.util.Date): ModelIterator = {
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
    val resultSets = (for (dayBucket <- dayBuckets)
      yield session.execute(
        select()
          .from(tablename)
          .where(QueryBuilder.eq("testcase_id", testcaseId))
          .and(QueryBuilder.eq("day_bucket", "2015-05-04"))
          .and(QueryBuilder.gte("testresult_datetime_run", datetime))
      ))
    new StatisticsModelIterator(resultSets)
  }
}
