import commons.enums.ClientError
import controllers.rest.base._
import play.api.http.HttpErrorHandler
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.Future

class GlobalErrorHandler extends HttpErrorHandler with RestResponses {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    Future.successful(badRequest(message, ClientError(statusCode)))
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    Future.successful(serverError(exception))
  }
}
