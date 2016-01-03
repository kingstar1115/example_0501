package controllers.base

import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, JsError}
import play.api.mvc.{Result, Controller}

import scala.concurrent.Future

abstract class BaseController() extends Controller with ApiActions {

  def jsonValidationFailed(errors: Seq[(JsPath, Seq[ValidationError])]) = validationFailed(JsError.toJson(errors))

  def wrapInFuture(result: Result) = Future.successful(result)
}
