package com.journeymonitor.analyze.api.controllers

import java.text.SimpleDateFormat
import java.util.Calendar

import akka.stream.scaladsl.Source
import com.journeymonitor.analyze.common.models.StatisticsModel
import com.journeymonitor.analyze.common.repositories.StatisticsRepository
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class Statistics(statisticsRepository: StatisticsRepository) extends Controller {

  implicit val dateFormat: Writes[java.util.Date] = new Writes[java.util.Date] {
    override def writes(o: java.util.Date): JsValue = {
      val sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ")
      sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
      JsString(sdf.format(o))
    }
  }

  implicit val StatisticsWrites = Json.writes[StatisticsModel]

  def showLatest(testcaseId: String, minTestresultDatetimeRun: String) = Action.async {
    Future {
      val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ")

      Try {
        sdf.parse(minTestresultDatetimeRun)
      } match {
        case Success(requestedMinTestresultDatetimeRunAsDate) =>

          /* If clients do not limit the request date or request results from many days ago,
             then we potentially have to run a huge amount of db queries (one for each day).
             Because this makes the whole operation really expensive, we never try to retrieve
             data older than 30 days.
           */
          val calendar30DaysAgo = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
          calendar30DaysAgo.add(Calendar.DATE, -30)
          val date30DaysAgo = calendar30DaysAgo.getTime
          val datetimeAsDateForDbRequest =
            if (date30DaysAgo.after(requestedMinTestresultDatetimeRunAsDate))
              date30DaysAgo
            else
              requestedMinTestresultDatetimeRunAsDate

          statisticsRepository.getAllForTestcaseIdSinceDatetime(testcaseId, datetimeAsDateForDbRequest) match {

            case Success(statisticsModelIterator: Iterator[StatisticsModel]) =>
              val modelsAsStringsIterator: Iterator[String] = for (statisticsModel <- statisticsModelIterator)
                yield Json.toJson(statisticsModel).toString + {
                  if (statisticsModelIterator.hasNext) "," else ""
                }

              val modelsAsStringsSource = Source.fromIterator(() => modelsAsStringsIterator)
              val beginSource = Source.fromIterator(() => List("[").iterator)
              val endSource = Source.fromIterator(() => List("]").iterator)

              Ok.chunked(beginSource ++ modelsAsStringsSource ++ endSource)
                .as("application/json; charset=utf-8")

            case Failure(ex) =>
              val cause = if (ex.getCause == null) ex else ex.getCause
              val message = cause match {
                case c: com.datastax.driver.core.exceptions.NoHostAvailableException
                => "Not enough database nodes available"
                case c: com.datastax.driver.core.exceptions.ReadTimeoutException
                => "Database read timeout"
                case c
                => c.getMessage
              }
              InternalServerError(Json.toJson(Map("message" -> ("An error occured: " + message))))
                .as("application/json; charset=utf-8")

          }
        case Failure(ex) => ex match {
          case e: java.text.ParseException
          => BadRequest(Json.toJson(
            Map("message" -> (s"Invalid minTestresultDatetimeRun format. You provided '$minTestresultDatetimeRun', "
              + "use yyyy-MM-dd HH:mm:ssZ (e.g. 2016-01-02 03:04:05+0600)")))
          ).as("application/json; charset=utf-8")
          case e
          => InternalServerError(Json.toJson(Map("message" -> ("An error occured: " + e.getMessage))))
            .as("application/json; charset=utf-8")
        }
      }
    }
  }

}
