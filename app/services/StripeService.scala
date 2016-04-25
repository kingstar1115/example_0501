package services

import java.util
import javax.inject.{Inject, Singleton}

import com.stripe.Stripe
import com.stripe.exception.StripeException
import com.stripe.model.{Card, Charge, Customer, DeletedExternalAccount}
import commons.enums.{ErrorType, ServerError, StripeError}
import play.api.Configuration
import services.StripeService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

@Singleton
class StripeService @Inject()(configuration: Configuration) {

  Stripe.apiKey = configuration.getString("stripe.key").get

  private def process[T](r: => T): Future[Either[ErrorResponse, T]] = {
    Future {
      Try(r) match {
        case Success(data) => Right(data)
        case Failure(e) =>
          e match {
            case e: StripeException =>
              Left(ErrorResponse(e.getMessage, StripeError))
            case e: Exception =>
              Left(ErrorResponse(e.getMessage, ServerError))
          }
      }
    }
  }

  def createCustomer(source: String, email: String): Future[Either[ErrorResponse, Customer]] = {
    val params = new util.HashMap[String, Object]() {
      put("source", source)
      put("email", email)
    }
    process(Customer.create(params))
  }

  def getCustomer(id: String): Future[Either[ErrorResponse, Customer]] = {
    process(Customer.retrieve(id))
  }

  def createCard(customer: Customer, source: String): Future[Either[ErrorResponse, Card]] = {
    val params = new util.HashMap[String, Object]() {
      put("source", source)
    }
    process(customer.getSources.create(params).asInstanceOf[Card])
  }

  def getCard(customer: Customer, cardId: String): Future[Either[ErrorResponse, Card]] = {
    process(customer.getSources.retrieve(cardId).asInstanceOf[Card])
  }

  def getCards(customer: Customer, limit: Int): Future[Either[ErrorResponse, List[Card]]] = {
    val params = new util.HashMap[String, Object]() {
      put("limit", limit:java.lang.Integer)
      put("object", "card")
    }
    process(customer.getSources.list(params)
      .getData.asScala.toList
      .filter(_.isInstanceOf[Card])
      .map(_.asInstanceOf[Card])
    )
  }

  def deleteCard(customer: Customer, cardId: String): Future[Either[ErrorResponse, DeletedExternalAccount]] = {
    process(customer.getSources.retrieve(cardId).delete)
  }

  def charge(amount: Int, paymentSource: PaymentSource, jobId: Long): Future[Either[ErrorResponse, Charge]] = {
    val metadata = new util.HashMap[String, String]() {
      put("jobId", jobId.toString)
    }
    val params = new util.HashMap[String, Object]() {
      put("amount", new Integer(amount))
      put("currency", "usd")
      put("metadata", metadata)
      put("customer", paymentSource.customerId)
    }
    paymentSource.sourceId match {
      case Some(source) => params.put("source", source)
      case _ =>
    }
    process(Charge.create(params))
  }
}

object StripeService {

  case class PaymentSource(customerId: String,
                           sourceId: Option[String])

  case class ErrorResponse(message: String,
                           errorType: ErrorType)

}
