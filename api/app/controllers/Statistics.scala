package controllers

import java.text.SimpleDateFormat

import com.journeymonitor.analyze.common.models.StatisticsModel
import com.journeymonitor.analyze.common.repositories.StatisticsRepository
import play.api.libs.functional.syntax._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.{Try, Success, Failure}

class Statistics(statisticsRepository: StatisticsRepository) extends Controller {

  implicit val StatisticsWrites = Json.writes[StatisticsModel]

  def showLatest(testcaseId: String, minTestresultDatetimeRun: String) = Action {
    val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ")

    // TODO: If no datetime is given, or a datetime from really long ago(tm), then
    // we are going to issue a lot of (unneeded) queries (one for each day).
    // Therefore, we need to decide on a maximum range (say, 100 days ago) for this
    // case, probably even returning a 400 or 500 error.

    Try {
      sdf.parse(minTestresultDatetimeRun)
    } match {
      case Success(datetime) =>
        statisticsRepository.getAllForTestcaseIdSinceDatetime(testcaseId, datetime) match {
          case Success(statisticsModelIterator: Iterator[StatisticsModel]) => {
            val modelsAsStringsIterator: Iterator[String] = for (statisticsModel <- statisticsModelIterator)
              yield Json.toJson(statisticsModel).toString + { if (statisticsModelIterator.hasNext) "," else "" }

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
      case Failure(ex) => ex match {
        case e: java.text.ParseException =>
          BadRequest(Json.toJson(Map("message" -> ("Invalid minTestresultDatetimeRun format, use yyyy-MM-dd HH:mm:ssZ (e.g. 2016-01-02 03:04:05+0600)"))))
        case e => InternalServerError(Json.toJson(Map("message" -> ("An error occured: " + e.getMessage))))
      }
    }
  }

}
