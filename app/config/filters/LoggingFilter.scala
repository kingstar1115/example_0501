package config.filters

import java.time.LocalDateTime

import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Filter, RequestHeader, Result}

import scala.concurrent.Future

class LoggingFilter extends Filter {

  val requestLogger = Logger("request")

  override def apply(next: RequestHeader => Future[Result])(request: RequestHeader): Future[Result] = {
    val authorization = getAuthorization(request)
    val startTime = System.currentTimeMillis

    requestLogger.info(s"[${LocalDateTime.now()}] - A ${request.method} request was received at ${request.uri} $authorization")
    next(request).map { response =>
      val requestTime = getRequestTime(startTime)
      requestLogger.info(s"[${LocalDateTime.now()}] - A $request $authorization took ${requestTime}ms => \n\t $response")
      response
    }
  }

  private def getAuthorization(request: RequestHeader): String = {
    request.headers.get("Authorization")
      .map(token => s"[Authorization : $token]")
      .getOrElse("")
  }

  private def getRequestTime(startTime: Long): Long = {
    val endTime = System.currentTimeMillis
    endTime - startTime
  }
}
