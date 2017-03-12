package commons

import commons.enums.ErrorType
import play.api.libs.json.{Json, Writes}

case class ServerError(message: String, errorType: Option[ErrorType] = None)

object ServerError {
  implicit val serverErrorWrites: Writes[ServerError] = Json.writes[ServerError]
}


