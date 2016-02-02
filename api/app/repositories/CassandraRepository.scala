package repositories

import com.datastax.driver.core.exceptions.ReadTimeoutException
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

  override def getNById(id: I, n: Int): Try[List[M]] = {
    Try {

      // There's probably a very clever functional approach to
      // "Try it 3 times, then finally fail", see e.g.
      // http://stackoverflow.com/questions/28506466/already-existing-functional-way-for-a-retry-until-in-scala
      try {
        val rows = getNBySinglePartitionKeyValue(id, n, 1).all().toList
        rows.map(row => rowToModel(row))
      } catch {
        case e: ReadTimeoutException => {
          try {
            val rows = getNBySinglePartitionKeyValue(id, n, 2).all().toList
            rows.map(row => rowToModel(row))
          } catch {
            case e: ReadTimeoutException => {
              val rows = getNBySinglePartitionKeyValue(id, n, 3).all().toList
              rows.map(row => rowToModel(row))
            }
          }
        }
      }

    }
  }

}
