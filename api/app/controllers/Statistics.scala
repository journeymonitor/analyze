package controllers

import com.journeymonitor.analyze.common.models.StatisticsModel
import com.journeymonitor.analyze.common.repositories.Repository
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._

import scala.util.{Success,Failure}

class Statistics(statisticsRepository: Repository[StatisticsModel, String]) extends Controller {

  implicit val StatisticsWrites: Writes[StatisticsModel] = (
    (JsPath \ "testresultId").write[String] and
    (JsPath \ "testresultDatetimeRun").write[java.util.Date] and
    (JsPath \ "runtimeMilliseconds").write[Int] and
    (JsPath \ "numberOf200").write[Int] and
    (JsPath \ "numberOf400").write[Int] and
    (JsPath \ "numberOf500").write[Int]
  )(unlift(StatisticsModel.unapply))

  def show(testcaseId: String, n: Int) = Action {
    statisticsRepository.getNById(testcaseId, n) match {
      case Success(statistics: List[StatisticsModel]) => Ok(Json.toJson(statistics))
      case Failure(ex) => InternalServerError(Json.toJson(Map("message" -> ("An error occured: " + ex.getMessage))))
    }
  }

}
