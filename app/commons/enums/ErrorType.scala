package commons.enums

sealed abstract class ErrorType(val name: String)

case object ValidationError extends ErrorType("EntityValidationError")

case object DatabaseError extends ErrorType("DatabaseError")

case object NotAuthorized extends ErrorType("NotAuthorized")

case object ServerError extends ErrorType("ServerError")

case class ClientError(code: Int) extends ErrorType(s"ClientError - $code")

case object FacebookError extends ErrorType("FacebookError")

case object AuthyError extends ErrorType("AuthyError")

case object CommonError extends ErrorType("CommonError")

case object StripeError extends ErrorType("StripeError")

