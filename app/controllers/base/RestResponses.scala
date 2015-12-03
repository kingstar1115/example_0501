package controllers.base


import commons.enums.{ValidationError, ErrorType}
import play.api.http.MimeTypes
import play.api.libs.json._
import play.api.mvc.Results._


trait RestResponses {

  def ok[X](data: X)(implicit writes: Writes[X]) = {
    Ok(Json.toJson[X](data)).as(MimeTypes.JSON)
  }

  def validationFailed[X](validationErrors: X)(implicit writes: Writes[X]) = {
    badRequest(validationErrors, ValidationError)
  }

  def badRequest[X](message: X, errorType: ErrorType)(implicit writes: Writes[X]) = {
    BadRequest(createErrorResponse(ErrorResponse(message, errorType.name))).as(MimeTypes.JSON)
  }

  private def createErrorResponse[X](error: ErrorResponse[X])(implicit writes: Writes[X]) = {
    Json.obj("message" -> Json.toJson[X](error.message), "code" -> JsString(error.code))
  }
}

case class ErrorResponse[X](message: X, code: String)

