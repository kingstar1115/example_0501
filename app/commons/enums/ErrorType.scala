package commons.enums

sealed abstract class ErrorType(val name: String)

case object ValidationError extends ErrorType("EntityValidationError")

