package controllers

import components.{Statistics, Repository, FakeCassandraClient}
import play.api._
import play.api.mvc._

class Application(statisticsRepository: Repository[Statistics, String, Array[String]]) extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready. " + statisticsRepository.getOneById("foo").testresultId))
  }

}
