package repositories

import com.datastax.driver.core.exceptions.{NoHostAvailableException, ReadTimeoutException}
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder._
import com.datastax.driver.core.{ResultSet, Row, Session}
import models.Model
import scala.collection.JavaConversions._
import scala.util.Try

abstract class CassandraRepository[M <: Model, I](session: Session, tablename: String, partitionKeyName: String)
  extends Repository[M, I] {
  def rowToModel(row: Row): M

  def getNBySinglePartitionKeyValue(partitionKeyValue: I, n: Int, nthTry: Int): ResultSet = {
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
  private def retry[T](numberOfTries: Int, originalNumberOfTries: Option[Int] = None)(fn: (Int) => T): T = {
    val actualOriginalNumberOfTries = originalNumberOfTries.getOrElse(numberOfTries)
    try {
      fn((actualOriginalNumberOfTries - numberOfTries) + 1)
    } catch {
      case e @ (_ : ReadTimeoutException | _: NoHostAvailableException) => {
        if (numberOfTries > 1) retry(numberOfTries - 1, Some(actualOriginalNumberOfTries))(fn)
        else throw e
      }
    }
  }

  override def getNById(id: I, n: Int): Try[List[M]] = {
    Try {
      val func = (nthTry: Int) => {
        val rows = getNBySinglePartitionKeyValue(id, n, nthTry).all().toList
        rows.map(row => rowToModel(row))
      }
      retry(3)(func)
    }
  }

}
