package repositories

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder._
import com.datastax.driver.core.{ResultSet, Row, Session}
import models.Model
import scala.collection.JavaConversions._
import scala.util.Try

abstract class CassandraRepository[M <: Model, I](session: Session, tablename: String, partitionKeyName: String)
  extends Repository[M, I] {
  def rowToModel(row: Row): M

  def getNBySinglePartitionKeyValue(partitionKeyValue: I, n: Int): ResultSet = {
    val selectStmt =
      select()
        .from(tablename)
        .where(QueryBuilder.eq(partitionKeyName, partitionKeyValue))
        .limit(n)

    session.execute(selectStmt)
  }

  override def getNById(id: I, n: Int): Try[List[M]] = {
    Try {
      val rows = getNBySinglePartitionKeyValue(id, n).all().toList
      rows.map(row => rowToModel(row))
    }
  }

}
