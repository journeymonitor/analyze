package com.journeymonitor.analyze.common.repositories

import java.text.SimpleDateFormat
import java.util.Calendar

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder._
import com.datastax.driver.core.{ResultSet, Row, Session}
import com.journeymonitor.analyze.common.models.StatisticsModel

import scala.collection._
import scala.util.Try

trait StatisticsRepository {
  /** Returns a ModelIterator over all statistics entries since the given date and time
    *
    * The dateTime is inclusive, i.e., the iterator will return models for all rows where the
    * value of the testresult_datetime_run column is identical to or younger than the given datetime.
    */
  def getAllForTestcaseIdSinceDatetime(testcaseId: String, datetime: java.util.Date): Try[Iterator[StatisticsModel]]
}

class StatisticsCassandraRepository(session: Session)
  extends CassandraRepository[StatisticsModel, String](session, "statistics", "testcase_id")
  with StatisticsRepository {

  class StatisticsModelIterator(val resultSets: Seq[ResultSet]) extends Iterator[StatisticsModel] {
    def next(): StatisticsModel = {
      val resultSet = resultSets.find(!_.isExhausted)
      resultSet match {
        case Some(r: ResultSet) => rowToModel(r.one())
        case None => throw new NoSuchElementException()
      }
    }

    def hasNext: Boolean = {
      resultSets.find(!_.isExhausted) match {
        case Some(_) => true
        case None => false
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

  private def getDayBuckets(datetime: java.util.Date): Seq[String] = {

    def asString(calendar: Calendar) = {
      val sdf = new SimpleDateFormat("yyyy-MM-dd");
      sdf.format(calendar.getTime)
    }

    val current = Calendar.getInstance()
    current.setTime(datetime)

    val today = Calendar.getInstance()

    if (current.after(today)) {
      Seq(asString(today))
    }

    val l = mutable.MutableList.empty[String]
    l += asString(current)

    while (asString(current) != asString(today)) {
      current.add(Calendar.DATE, 1)
      l += asString(current)
    }

    l.toSeq.reverse
  }

  def getAllForTestcaseIdSinceDatetime(testcaseId: String, datetime: java.util.Date): Try[Iterator[StatisticsModel]] = {
    Try {
      val dayBuckets = getDayBuckets(datetime)
      val resultSets = for (dayBucket <- dayBuckets)
        yield session.execute(
          select()
            .from(tablename)
            .where(QueryBuilder.eq("testcase_id", testcaseId))
              .and(QueryBuilder.eq("day_bucket", dayBucket))
              .and(QueryBuilder.gte("testresult_datetime_run", datetime))
        )
      new StatisticsModelIterator(resultSets)
    }
  }
}
