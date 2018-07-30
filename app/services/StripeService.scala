package services

import java.util

import com.stripe.Stripe
import com.stripe.exception.StripeException
import com.stripe.model._
import com.stripe.net.RequestOptions
import commons.enums.{ErrorType, InternalSError, StripeError}
import controllers.rest.TasksController.TipDto
import javax.inject.{Inject, Singleton}
import models.Tables._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.{Configuration, Logger}
import services.StripeService._

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class StripeService @Inject()(configuration: Configuration) {

  val logger = Logger(this.getClass)

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

  def createCustomerIfNotExists(email: String): Customer = {
    val params = new util.HashMap[String, Object]() {
      put("email", email)
    }
    val customers = Customer.list(params).getData

    if (customers.isEmpty) Customer.create(params) else customers.get(0)
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

  def charge(chargeRequest: ChargeRequest, metaData: Map[String, String] = Map.empty): Future[Either[ErrorResponse, Charge]] = {
    chargeRequest match {
      case TokenCharge(Some(email), source, _, _) =>
        val customer = createCustomerIfNotExists(email)
        linkTokenToCustomerAndCharge(chargeRequest, metaData, customer, source)

      case TokenCharge(None, token, _, _) =>
        logger.warn(s"Charging `$token` token for anonymous")
        chargeInternal(chargeRequest, metaData) { chargeParameters =>
          chargeParameters.put("source", token)
        }

      case tokenCharge: CustomerTokenCharge =>
        val customer = Customer.retrieve(tokenCharge.customerId)
        linkTokenToCustomerAndCharge(tokenCharge, metaData, customer, tokenCharge.token)

      case cardCharge: CustomerCardCharge =>
        chargeInternal(chargeRequest, metaData) { chargeParameters =>
          chargeParameters.put("customer", cardCharge.customerId)
          cardCharge.source match {
            case Some(source) => chargeParameters.put("source", source)
            case _ =>
          }
        }
    }
  }

  private def linkTokenToCustomerAndCharge(chargeRequest: ChargeRequest, metaData: Map[String, String], customer: Customer, source: String) = {
    val params = new util.HashMap[String, Object]() {
      put("source", source)
    }
    val linkedCard = customer.getSources.create(params).asInstanceOf[Card]

    chargeInternal(chargeRequest, metaData) { chargeParameters =>
      chargeParameters.put("customer", customer.getId)
      chargeParameters.put("source", linkedCard.getId)
    }.map(chargeResult => {
      linkedCard.delete()
      chargeResult
    })
  }

  private def chargeInternal(chargeRequest: ChargeRequest, metaData: Map[String, String])
                            (addParameters: util.HashMap[String, Object] => Unit) = {
    process {
      val metadataMap = new util.HashMap[String, String]() {
        metaData.foreach(entry => put(entry._1, entry._2))
      }
      val params = new util.HashMap[String, Object]() {
        put("amount", new Integer(chargeRequest.amount))
        put("currency", "usd")
        put("description", chargeRequest.description)
        put("metadata", metadataMap)
      }
      addParameters(params)
      Charge.create(params)
    }
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

  def createEphemeralKey(customerId: String, apiVersion: String): Future[Either[ErrorResponse, EphemeralKey]] = {
    val params = new util.HashMap[String, Object]() {
      put("customer", customerId)
    }
    val requestOptions = new RequestOptions.RequestOptionsBuilder()
      .setStripeVersion(apiVersion)
      .build()
    process(EphemeralKey.create(params, requestOptions))
  }

  def createTipCharge(user: UsersRow, tip: Option[TipDto]): Option[ChargeRequest] = {
    val chargeRequest = user.stripeId.map { id =>
      tip match {
        case Some(TipDto(amount, _, Some(token))) =>
          Some(CustomerTokenCharge(id, token, amount, TipDescription))
        case Some(TipDto(amount, cardId, None)) =>
          Some(CustomerCardCharge(id, cardId, amount, TipDescription))
        case _ =>
          None
      }
    } getOrElse {
      tip match {
        case Some(TipDto(amount, _, Some(token))) =>
          Some(TokenCharge(user.email, token, amount, TipDescription))
        case Some(TipDto(amount, Some(cardId), None)) =>
          Some(TokenCharge(user.email, cardId, amount, TipDescription))
        case _ =>
          None
      }
    }
    if (tip.isDefined && chargeRequest.isEmpty) {
      logger.warn(s"Failed to create charge request user(${user.email}, ${user.stripeId}). Tip: $tip")
    }
    chargeRequest
  }
}

object StripeService {

  val TipDescription = "Tip"

  sealed trait ChargeRequest {
    def amount: Int

    def description: String
  }

  case class TokenCharge(email: Option[String],
                         token: String,
                         amount: Int,
                         description: String) extends ChargeRequest

  object TokenCharge {

    def apply(email: String, source: String, amount: Int, description: String): TokenCharge =
      new TokenCharge(Some(email), source, amount, description)
  }

  case class CustomerTokenCharge(customerId: String,
                                 token: String,
                                 amount: Int,
                                 description: String) extends ChargeRequest

  case class CustomerCardCharge(customerId: String,
                                source: Option[String],
                                amount: Int,
                                description: String) extends ChargeRequest

  case class ErrorResponse(message: String,
                           errorType: ErrorType)

}
