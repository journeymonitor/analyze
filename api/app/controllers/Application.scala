package controllers

import play.api._
import play.api.libs.ws.WSClient
import play.api.mvc._

class Application(ws: WSClient) extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

}
