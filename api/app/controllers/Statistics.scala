package controllers

import java.text.SimpleDateFormat

import com.journeymonitor.analyze.common.models.StatisticsModel
import com.journeymonitor.analyze.common.repositories.StatisticsRepository
import play.api.libs.functional.syntax._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global

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
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ")

    statisticsRepository.getAllForTestcaseIdSinceDatetime(testcaseId, sdf.parse(minTestresultDatetimeRun)) match {
      case Success(statisticsModelIterator: Iterator[StatisticsModel]) => {
        val modelsAsStringsIterator = for (statisticsModel <- statisticsModelIterator)
          yield (Json.toJson(statisticsModel).toString + { if (statisticsModelIterator.hasNext) "," else "" })

        val enumeratedModels = Enumerator.enumerate(modelsAsStringsIterator)
        val begin = Enumerator.enumerate(List("["))
        val end = Enumerator.enumerate(List("]"))

        Ok.chunked(
          begin andThen(
            enumeratedModels andThen(
              end andThen(
                Enumerator.eof
              )
            )
          )
        ).as("application/json; charset=utf-8")
      }
      case Failure(ex) => InternalServerError(Json.toJson(Map("message" -> ("An error occured: " + ex.getMessage))))
    }
  }

}
