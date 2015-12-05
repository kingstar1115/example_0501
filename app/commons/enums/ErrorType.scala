package commons.enums

sealed abstract class ErrorType(val name: String)

case object ValidationError extends ErrorType("EntityValidationError")

case object DatabaseError extends ErrorType("DatabaseError")

case object NotAuthorized extends ErrorType("NotAuthorized")

