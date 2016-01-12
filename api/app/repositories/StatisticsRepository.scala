package repositories

import com.datastax.driver.core.{Row, Session}
import models.StatisticsModel

class StatisticsRepository(session: Session)
  extends CassandraRepository[StatisticsModel, String](session, "statistics", "testcase_id") {
  override def rowToModel(row: Row): StatisticsModel = {
    StatisticsModel(row.getString("testresult_id"), row.getInt("number_of_200"))
  }
}
