package controllers.api

import play.api.mvc._
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class Thing(foo: String, bar: String)

class Statistics extends Controller {

  implicit val ThingWrites: Writes[Thing] = (
    (JsPath \ "foo").write[String] and
    (JsPath \ "bar").write[String]
  )(unlift(Thing.unapply))

  def show(id: String) = Action {
    Ok(Json.toJson(Thing("a", "b")))
  }

}
