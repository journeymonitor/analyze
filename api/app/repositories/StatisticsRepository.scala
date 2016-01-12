package repositories

import com.datastax.driver.core.{Row, Session}
import models.Statistics

class StatisticsRepository(session: Session)
  extends CassandraRepository[Statistics, String](session, "statistics", "testcase_id") {
  override def rowToModel(row: Row): Statistics = {
    Statistics(row.getString("testresult_id"), row.getInt("number_of_200"))
  }
}
