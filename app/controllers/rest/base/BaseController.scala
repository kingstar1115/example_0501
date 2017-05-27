package controllers.rest.base

import play.api.data.validation.ValidationError
import play.api.libs.json.{JsError, JsPath, JsValue, Reads}
import play.api.mvc.{Controller, Result}

import scala.concurrent.Future

trait BaseController extends Controller with ApiActions {

  def jsonValidationFailed(errors: Seq[(JsPath, Seq[ValidationError])]) = validationFailed(JsError.toJson(errors))

  def jsonValidationFailedF(errors: Seq[(JsPath, Seq[ValidationError])]) =
    Future.successful(validationFailed(JsError.toJson(errors)))

  def processRequest[T](jsonBody: JsValue)(valid: T => Result)(implicit reads: Reads[T]) = {
    jsonBody.validate.fold(jsonValidationFailed, valid)
  }

  def processRequestF[T](jsonBody: JsValue)(valid: T => Future[Result])(implicit reads: Reads[T]) = {
    jsonBody.validate.fold(jsonValidationFailedF, valid)
  }
}
