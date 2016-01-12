package controllers

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc._
import repositories.Repository

class Statistics(statisticsRepository: Repository[models.Statistics, String]) extends Controller {

  implicit val StatisticsWrites: Writes[models.Statistics] = (
    (JsPath \ "testresultId").write[String] and
    (JsPath \ "numberOf200").write[Int]
  )(unlift(models.Statistics.unapply))

  def show(testcaseId: String) = Action {
    val statistics = statisticsRepository.getOneById(testcaseId)
    Ok(Json.toJson(statistics))
  }

}
