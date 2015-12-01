package controllers.base


import commons.enums.{ValidationError, ErrorType}
import controllers.base.RestResponses.ErrorResponse
import play.api.http.MimeTypes
import play.api.libs.json.Json
import play.api.mvc.Results._


trait RestResponses {

  implicit val errorResponseReads = Json.format[ErrorResponse]

  def validationFailed(validationErrors: String) = {
    badRequest(validationErrors, ValidationError)
  }

  def badRequest(message: String, errorType: ErrorType) = {
    BadRequest(Json.toJson(ErrorResponse(message, errorType.name))).as(MimeTypes.JSON)
  }
}

object RestResponses {

  case class ErrorResponse(message: String, code: String)

}
