package controllers.base


import commons.enums.{ServerError, ValidationError, ErrorType}
import play.api.http.MimeTypes
import play.api.libs.json._
import play.api.mvc.Results._


trait RestResponses {

  implicit val simpleResponseFormat = Json.format[SimpleResponse]

  def ok[X](data: X)(implicit writes: Writes[X]) = {
    Ok(Json.toJson[X](data)).as(MimeTypes.JSON)
  }

  def validationFailed[X](validationErrors: X)(implicit writes: Writes[X]) = {
    badRequest(validationErrors, ValidationError)
  }

  def badRequest[X](message: X, errorType: ErrorType)(implicit writes: Writes[X]) = {
    BadRequest(createErrorResponse(ErrorResponse(message, errorType.name))).as(MimeTypes.JSON)
  }

  def forbidden[X](message: X, errorType: ErrorType)(implicit writes: Writes[X]) = {
    Forbidden(createErrorResponse(ErrorResponse(message, errorType.name))).as(MimeTypes.JSON)
  }

  def serverError(t: Throwable) = {
    InternalServerError(createErrorResponse(ErrorResponse(t.getMessage, ServerError.name))).as(MimeTypes.JSON)
  }

  private def createErrorResponse[X](error: ErrorResponse[X])(implicit writes: Writes[X]) = {
    Json.obj("message" -> Json.toJson[X](error.message), "code" -> JsString(error.code))
  }
}

case class ErrorResponse[X](message: X, code: String)

case class SimpleResponse(message: String)


