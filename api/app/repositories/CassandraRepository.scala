package repositories

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder._
import com.datastax.driver.core.{Row, Session}
import models.Model

abstract class CassandraRepository[M <: Model, I](session: Session, tablename: String, partitionKeyName: String)
  extends Repository[M, I] {
  def rowToModel(row: Row): M

  def getOneRowBySinglePartitionKeyId(partitionKeyValue: I): Row = {
    val selectStmt =
      select()
        .from(tablename)
        .where(QueryBuilder.eq(partitionKeyName, partitionKeyValue))
        .limit(1)

    val resultSet = session.execute(selectStmt)
    val row = resultSet.one()
    row
  }

  override def getOneById(id: I): M = {
    val row = getOneRowBySinglePartitionKeyId(id)
    rowToModel(row)
  }
}
