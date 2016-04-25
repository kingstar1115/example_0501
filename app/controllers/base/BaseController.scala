package controllers.base

import play.api.data.validation.ValidationError
import play.api.libs.json.{JsError, JsPath, JsValue, Reads}
import play.api.mvc.{Controller, Result}

import scala.concurrent.Future

trait BaseController extends Controller with ApiActions {

  def jsonValidationFailed(errors: Seq[(JsPath, Seq[ValidationError])]) = validationFailed(JsError.toJson(errors))

  def jsonValidationFailedF(errors: Seq[(JsPath, Seq[ValidationError])]) =
    wrapInFuture(validationFailed(JsError.toJson(errors)))

  def wrapInFuture(result: Result) = Future.successful(result)

  def processRequest[T](jsonBody: JsValue)(f: T => Future[Result])(implicit reads: Reads[T]) = {
    jsonBody.validate.fold(jsonValidationFailedF, f)
  }
}
