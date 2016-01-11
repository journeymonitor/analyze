package controllers

import models.Statistics
import play.api._
import play.api.mvc._
import repositories.Repository

class Application(statisticsRepository: Repository[Statistics, String]) extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready. " + statisticsRepository.getOneById("testcase1").testresultId))
  }

}
