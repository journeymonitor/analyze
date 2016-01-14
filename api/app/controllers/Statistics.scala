package controllers

import models.StatisticsModel
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import repositories.Repository

class Statistics(statisticsRepository: Repository[StatisticsModel, String]) extends Controller {

  implicit val StatisticsWrites: Writes[StatisticsModel] = (
    (JsPath \ "testresultId").write[String] and
    (JsPath \ "runtimeMilliseconds").write[Int] and
    (JsPath \ "numberOf200").write[Int] and
    (JsPath \ "numberOf400").write[Int] and
    (JsPath \ "numberOf500").write[Int]
  )(unlift(StatisticsModel.unapply))

  def show(testcaseId: String) = Action {
    val statistics = statisticsRepository.getOneById(testcaseId)
    Ok(Json.toJson(statistics))
  }

}
