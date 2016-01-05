package controllers

import components.{AbstractStatisticsRepository, FakeCassandraClient}
import play.api._
import play.api.mvc._

class Application(statisticsRepository: AbstractStatisticsRepository) extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready. " + statisticsRepository.getOneById("foo").testresultId))
  }

}
