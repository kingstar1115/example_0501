package services

import java.util
import javax.inject.{Inject, Singleton}

import com.stripe.Stripe
import com.stripe.exception.StripeException
import com.stripe.model._
import commons.enums.{ErrorType, InternalSError, StripeError}
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits._
import services.StripeService._

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

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
              Left(ErrorResponse(e.getMessage, InternalSError))
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
      put("limit", limit: java.lang.Integer)
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

  def chargeFromCard(amount: Int, paymentSource: PaymentSource, description: String, metaData: Map[String, String] = Map.empty): Future[Either[ErrorResponse, Charge]] = {
    chargeInternal(amount, description, metaData)({ parameters =>
      parameters.put("customer", paymentSource.customerId)
      paymentSource.sourceId match {
        case Some(source) => parameters.put("source", source)
        case _ =>
      }
    })
  }

  def chargeFromToken(amount: Int, source: String, description: String, metaData: Map[String, String] = Map.empty): Future[Either[ErrorResponse, Charge]] = {
    chargeInternal(amount, description, metaData)({ parameters =>
      parameters.put("source", source)
    })
  }

  private def chargeInternal(amount: Int, description: String, metaData: Map[String, String])(addParameters: (util.HashMap[String, Object]) => Unit) = {
    val metadataMap = new util.HashMap[String, String]() {
      metaData.foreach(entry => put(entry._1, entry._2))
    }
    val params = new util.HashMap[String, Object]() {
      put("amount", new Integer(amount))
      put("currency", "usd")
      put("description", description)
      put("metadata", metadataMap)
    }
    addParameters(params)
    process(Charge.create(params))
  }

  def updateChargeMetadata(charge: Charge, jobId: Long): Future[Either[ErrorResponse, Charge]] = {
    val metadata = new util.HashMap[String, String]() {
      put("jobId", jobId.toString)
    }
    val params = new util.HashMap[String, Object]() {
      put("metadata", metadata)
    }
    process(charge.update(params))
  }

  def refund(chargeId: String): Future[Either[ErrorResponse, Refund]] = {
    val params = new util.HashMap[String, Object]() {
      put("charge", chargeId)
    }
    process(Refund.create(params))
  }
}

object StripeService {

  case class PaymentSource(customerId: String,
                           sourceId: Option[String])

  case class ErrorResponse(message: String,
                           errorType: ErrorType)

}
