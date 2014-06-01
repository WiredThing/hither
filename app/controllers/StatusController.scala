package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.json.JsBoolean

object StatusController extends Controller {

  def ping = Action { request => Ok(JsBoolean(true))}

}
