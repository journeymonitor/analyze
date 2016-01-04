package controllers

import components.CassandraClient
import play.api._
import play.api.mvc._

class Application(cc: CassandraClient) extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready. " + cc.theUrl))
  }

}
