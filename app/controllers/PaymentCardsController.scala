package controllers

import javax.inject.Inject

import com.stripe.model.Card
import controllers.PaymentCardsController._
import controllers.base.{BaseController, ListResponse}
import models.Tables.{UsersRow, _}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import play.api.mvc.Result
import security.TokenStorage
import services.StripeService
import services.StripeService.ErrorResponse
import slick.driver.PostgresDriver.api._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class PaymentCardsController @Inject()(val tokenStorage: TokenStorage,
                                       dbConfigProvider: DatabaseConfigProvider,
                                       stripeService: StripeService) extends BaseController {

  implicit val paymentSourceReads = (__ \ 'source).read[String].map(PaymentSource.apply)
  implicit val cardDtoFormat = Json.format[CardDto]

  val db = dbConfigProvider.get.db

  def addPaymentCard() = authorized.async(parse.json) { request =>
    processRequest[PaymentSource](request.body) { dto =>
      val userId = request.token.get.userInfo.id

      def addPaymentSource(stripeId: String) = {
        processStripe(stripeService.getCustomer(stripeId)) { customer =>
          processStripe(stripeService.createCard(customer, dto.source))(_ => Future(NoContent))
        }
      }

      def createNewUser(user: UsersRow) = {
        processStripe(stripeService.createCustomer(dto.source, user.email)) { customer =>
          val userUpdateQuery = for {
            user <- Users if user.id === userId
          } yield user.stripeId
          db.run(userUpdateQuery.update(Option(customer.getId))).map(_ => NoContent)
        }
      }

      val userQuery = for {
        user <- Users if user.id === userId
      } yield user
      db.run(userQuery.result.head)
        .flatMap { user =>
          user.stripeId.map(addPaymentSource)
            .getOrElse(createNewUser(user))
        }
    }
  }

  def removePaymentCard() = authorized.async(parse.json) { request =>
    processRequest[PaymentSource](request.body) { dto =>
      val userId = request.token.get.userInfo.id
      val userQuery = for {
        user <- Users if user.id === userId && user.stripeId.isDefined
      } yield user

      db.run(userQuery.result.headOption).flatMap { userOpt =>
        userOpt.map { user =>
          processStripe(stripeService.getCustomer(user.stripeId.get)) { customer =>
            processStripe(stripeService.deleteCard(customer, dto.source))(_ => Future(NoContent))
          }
        }.getOrElse(Future.successful(badRequest("Payment system doesn't attached")))
      }
    }
  }

  def listPaymentCards = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val userQuery = for {
      user <- Users if user.id === userId && user.stripeId.isDefined
    } yield user

    db.run(userQuery.result.headOption).flatMap { userOpt =>
      userOpt.map { user =>
        processStripe(stripeService.getCustomer(user.stripeId.get)) { customer =>
          processStripe(stripeService.getCards(customer, 0, 100)) { cardsResponse =>
            val cards = cardsResponse.getData.asScala.toList.map(_.toDto)
            Future(ok(ListResponse(cards, 0, 100, cards.size)))
          }
        }
      }.getOrElse(Future.successful(badRequest("Payment system doesn't attached")))
    }
  }

  def processStripe[T](result: Future[Either[ErrorResponse, T]])(f: T => Future[Result]) = {
    result.flatMap {
      case Left(error) => Future.successful(badRequest(error.message, error.errorType))
      case Right(data) => f(data)
    }
  }
}

object PaymentCardsController {

  implicit class CardExt(card: Card) {
    def toDto = {
      CardDto(card.getId, card.getBrand, card.getCountry, card.getDynamicLast4, card.getExpMonth, card.getExpYear,
        card.getLast4, card.getFunding)
    }
  }

  case class PaymentSource(source: String)

  case class CardDto(id: String,
                     brand: String,
                     country: String,
                     dynamicLast4: String,
                     expMonth: Int,
                     expYear: Int,
                     last4: String,
                     funding: String)

}
