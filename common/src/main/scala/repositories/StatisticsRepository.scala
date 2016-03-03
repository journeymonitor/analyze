package com.journeymonitor.analyze.common.repositories

import com.datastax.driver.core.exceptions.{NoHostAvailableException, ReadTimeoutException}
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder._
import com.datastax.driver.core.{ResultSet, ResultSetFuture, Row, Session}
import com.journeymonitor.analyze.common.models.StatisticsModel
import com.journeymonitor.analyze.common.util.Util

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
      resultSets.exists(!_.isExhausted)
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

  // Based on http://stackoverflow.com/a/7931459/715256
  private def retry[T](numberOfTries: Int, originalNumberOfTries: Option[Int] = None)(fn: (Int) => T): T = {
    val actualOriginalNumberOfTries = originalNumberOfTries.getOrElse(numberOfTries)
    try {
      fn((actualOriginalNumberOfTries - numberOfTries) + 1)
    } catch {
      case ex: java.util.concurrent.ExecutionException => {
        ex.getCause match {
          case e @ (_ : ReadTimeoutException | _: NoHostAvailableException) => {
            if (numberOfTries > 1) retry(numberOfTries - 1, Some(actualOriginalNumberOfTries))(fn)
            else throw e
          }
        }
      }
    }
  }

  private def getAllForTestcaseIdAndDayBucketSinceDatetime(testcaseId: String, dayBucket: String, datetime: java.util.Date, nthTry: Int): ResultSetFuture = {
    session.executeAsync(
      select()
        .from(tablename)
        .where(QueryBuilder.eq("testcase_id", testcaseId))
        .and(QueryBuilder.eq("day_bucket", dayBucket))
        .and(QueryBuilder.gte("testresult_datetime_run", datetime))
        + " /* " + nthTry + ". try */"
    )
  }

  def getAllForTestcaseIdSinceDatetime(testcaseId: String, datetime: java.util.Date): Try[Iterator[StatisticsModel]] = {
    Try {
      val dayBuckets = Util.getDayBuckets(datetime)
      val func = (nthTry: Int) => {
        val resultSetFutures = for (dayBucket <- dayBuckets)
          yield getAllForTestcaseIdAndDayBucketSinceDatetime(testcaseId, dayBucket, datetime, nthTry)
        resultSetFutures.map(_.get())
      }

      val resultSets = retry(3)(func)

      /*
      Executing asynchronously and then immediately resolving all futures via get()
      may look counter-intuitive, but results in a running-time optimization nonetheless.
      We don't need to wait for a query to finish before starting the next, we can fire them
      all in parallel, which results in a much lower total run time for all queries.
       */

      new StatisticsModelIterator(resultSets)
    }
  }
}
