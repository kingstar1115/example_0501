import commons.enums.ClientError
import controllers.rest.base._
import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.Future

class GlobalErrorHandler extends HttpErrorHandler with RestResponses {

  private val logger = Logger(this.getClass)

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    if (request.path.startsWith("/api") || request.path.startsWith("/admin")) {
      logger.info(s"${request.uri} request ended with client error: $message - $statusCode")
    }
    Future.successful(badRequest(message, ClientError(statusCode)))
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    logger.error(s"${request.uri} requested ended with server error: ${exception.getMessage}", exception)
    Future.successful(serverError(exception))
  }
}
