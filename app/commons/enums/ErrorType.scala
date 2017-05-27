package commons.enums

import play.api.libs.json.{Json, Writes}

sealed abstract class ErrorType(val name: String)

object ErrorType {
  implicit val errorTypeFormat: Writes[ErrorType] = Writes((errorType: ErrorType) => Json.obj("code" -> errorType.name))
}

case object ValidationError extends ErrorType("EntityValidationError")

case object DatabaseError extends ErrorType("DatabaseError")

case object NotAuthorized extends ErrorType("NotAuthorized")

case object InternalSError extends ErrorType("ServerError")

case class ClientError(code: Int) extends ErrorType(s"ClientError - $code")

case object FacebookError extends ErrorType("FacebookError")

case object AuthyError extends ErrorType("AuthyError")

case object CommonError extends ErrorType("CommonError")

case object StripeError extends ErrorType("StripeError")

case object TookanError extends ErrorType("TookanError")

case object BadRequest extends ErrorType("Bad Request")

