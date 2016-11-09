package commons

import commons.enums.ErrorType

case class ServerError(message: String, errorType: Option[ErrorType] = None)
