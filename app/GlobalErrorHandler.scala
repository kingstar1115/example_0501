import commons.enums.ClientError
import controllers.base.RestResponses
import play.api.Logger
import play.api.http.HttpErrorHandler
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.Future

class GlobalErrorHandler extends HttpErrorHandler with RestResponses{

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    Logger.info(s"Failed to accept request: ${request.queryString}")
    Future.successful(badRequest(message, ClientError(statusCode)))
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    Future.successful(serverError(exception))
  }
}
