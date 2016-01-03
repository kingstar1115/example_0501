package controllers.base


import commons.enums.{ServerError, ValidationError, ErrorType}
import play.api.http.MimeTypes
import play.api.libs.json._
import play.api.mvc.Results._


trait RestResponses {

  def ok[X](data: X)(implicit writes: Writes[X]) = {
    Ok(toJson(ApiResponse(data))).as(MimeTypes.JSON)
  }

  def validationFailed[X](validationErrors: X)(implicit writes: Writes[X]) = {
    badRequest(validationErrors, ValidationError)
  }

  def badRequest[X](message: X, errorType: ErrorType)(implicit writes: Writes[X]) = {
    BadRequest(toJson(ApiResponse(message, errorType.name))).as(MimeTypes.JSON)
  }

  def badRequest[X](message: X)(implicit writes: Writes[X]) = {
    BadRequest(toJson(ApiResponse(message, "400"))).as(MimeTypes.JSON)
  }

  def forbidden[X](message: X, errorType: ErrorType)(implicit writes: Writes[X]) = {
    Forbidden(toJson(ApiResponse(message, errorType.name))).as(MimeTypes.JSON)
  }

  def serverError(t: Throwable) = {
    InternalServerError(toJson(ApiResponse(t.getMessage, ServerError.name))).as(MimeTypes.JSON)
  }

  def created(url: String) = {
    Created(toJson(ApiResponse(url, "201"))).as(MimeTypes.JSON)
  }

  def notFound = {
    NotFound(toJson(ApiResponse("Resource not found", "404"))).as(MimeTypes.JSON)
  }

  def toJson[X](response: ApiResponse[X])(implicit writes: Writes[X]) = {
    Json.obj("message" -> Json.toJson[X](response.message), "code" -> JsString(response.code))
  }
}

case class ApiResponse[X](message: X, code: String = "200")


