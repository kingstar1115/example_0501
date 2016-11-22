package controllers.base


import commons.enums.{ErrorType, InternalSError, ValidationError}
import play.api.http.MimeTypes
import play.api.libs.json._
import play.api.mvc.Results._


trait RestResponses {

  implicit def listResultsWrites[X](implicit fmt: Writes[X]): Writes[ListResponse[X]] = new Writes[ListResponse[X]] {
    def writes(result: ListResponse[X]) = JsObject(Seq(
      "limit" -> JsNumber(result.limit),
      "offset" -> JsNumber(result.offset),
      "total" -> JsNumber(result.total),
      "items" -> JsArray(result.items.map(Json.toJson(_)))
    ))
  }

  def success = ok("Successfully")

  def ok[X](data: X, code: String = "200")(implicit writes: Writes[X]) = {
    Ok(toJson(ApiResponse(data, code))).as(MimeTypes.JSON)
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
    InternalServerError(toJson(ApiResponse(t.getMessage, InternalSError.name))).as(MimeTypes.JSON)
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

case class ApiResponse[X](message: X,
                          code: String = "200")

case class ListResponse[X](items: Seq[X],
                           limit: Int,
                           offset: Int,
                           total: Int)


