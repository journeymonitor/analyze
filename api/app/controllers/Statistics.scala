package controllers

import java.text.SimpleDateFormat

import com.journeymonitor.analyze.common.models.StatisticsModel
import com.journeymonitor.analyze.common.repositories.{ModelIterator, StatisticsRepository}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._

import scala.util.{Success,Failure}

class Statistics(statisticsRepository: StatisticsRepository) extends Controller {

  implicit val StatisticsWrites: Writes[StatisticsModel] = (
    (JsPath \ "testresultId").write[String] and
    (JsPath \ "testresultDatetimeRun").write[java.util.Date] and
    (JsPath \ "runtimeMilliseconds").write[Int] and
    (JsPath \ "numberOf200").write[Int] and
    (JsPath \ "numberOf400").write[Int] and
    (JsPath \ "numberOf500").write[Int]
  )(unlift(StatisticsModel.unapply))

  def showLatest(testcaseId: String, minTestresultDatetimeRun: String) = Action {
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:ii:ss+0000")
    statisticsRepository.getAllForTestcaseIdSinceDatetime(testcaseId, sdf.parse(minTestresultDatetimeRun)) match {
      case Success(statisticsModelIterator: ModelIterator[StatisticsModel]) => {
        val statisticsModels = for (statisticsModel <- statisticsModelIterator.next()) // TODO: Streaming response
          yield statisticsModel
        Ok(Json.toJson(statisticsModels.get)) // Meh.
      }
      case Failure(ex) => InternalServerError(Json.toJson(Map("message" -> ("An error occured: " + ex.getMessage))))
    }
  }

}
