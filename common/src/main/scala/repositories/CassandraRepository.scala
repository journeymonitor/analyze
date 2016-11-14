package com.journeymonitor.analyze.common.repositories

import com.datastax.driver.core.exceptions.{NoHostAvailableException, ReadTimeoutException}
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder._
import com.datastax.driver.core.{ResultSet, Row, Session}
import com.journeymonitor.analyze.common.models.Model

abstract class CassandraRepository[M <: Model, I](session: Session, val tablename: String, partitionKeyName: String) {
  def rowToModel(row: Row): M

  private def getNBySinglePartitionKeyValue(partitionKeyValue: I, n: Int, nthTry: Int): ResultSet = {
    val selectStmt =
      select()
        .from(tablename)
        .where(QueryBuilder.eq(partitionKeyName, partitionKeyValue))
        .limit(n) + " /* " + nthTry + ". try */"
      // We add the ' /* N. try */' CQL comment because right now it seems to
      // be the only way to make stubbed cassandra priming work if we want
      // to simulate "fail 2 times with read timeout, then work"

      session.execute(selectStmt)
  }

  // Based on http://stackoverflow.com/a/7931459/715256
  def retry[T](numberOfTries: Int, originalNumberOfTries: Option[Int] = None)(fn: (Int) => T): T = {
    val actualOriginalNumberOfTries = originalNumberOfTries.getOrElse(numberOfTries)
    try {
      fn((actualOriginalNumberOfTries - numberOfTries) + 1)
    } catch {
      case ex: java.util.concurrent.ExecutionException =>
        ex.getCause match {
          case e @ (_ : ReadTimeoutException | _: NoHostAvailableException) => {
            if (numberOfTries > 1) retry(numberOfTries - 1, Some(actualOriginalNumberOfTries))(fn)
            else throw e
          }
        }
    }
  }

}
