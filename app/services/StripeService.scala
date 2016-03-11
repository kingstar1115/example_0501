package services

import java.util
import javax.inject.{Inject, Singleton}

import com.stripe.Stripe
import com.stripe.exception.StripeException
import com.stripe.model.Charge
import commons.enums.{ErrorType, ServerError, StripeError}
import play.api.Configuration
import services.StripeService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class StripeService @Inject()(configuration: Configuration) {

  Stripe.apiKey = configuration.getString("stripe.key").get

  def charge(amount: Int, source: String, jobId: Long): Future[Either[ErrorResponse, Charge]] = {
    val metadata = new util.HashMap[String, String]() {
      put("jobId", jobId.toString)
    }
    val params = new util.HashMap[String, Object]() {
      put("amount", new Integer(amount))
      put("currency", "usd")
      put("source", source)
      put("metadata", metadata)
    }
    Future {
      Try(Charge.create(params)) match {
        case Success(charge) => Right(charge)
        case Failure(e) => e match {
          case e: StripeException => Left(ErrorResponse(e.getMessage, StripeError))
          case e: Exception => Left(ErrorResponse(e.getMessage, ServerError))
        }
      }
    }
  }
}

object StripeService {

  case class ErrorResponse(message: String,
                           errorType: ErrorType)

}
