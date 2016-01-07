package controllers

import components.{Statistics, Repository}
import play.api._
import play.api.mvc._

class Application(statisticsRepository: Repository[Statistics, String]) extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready. " + statisticsRepository.getOneById("testcase1").testresultId))
  }

}
