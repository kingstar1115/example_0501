package controllers.rest

import com.stripe.model.{Card, EphemeralKey, EphemeralKeyAssociatedObject}
import controllers.rest.PaymentCardsController._
import controllers.rest.base._
import javax.inject.Inject
import models.Tables.{UsersRow, _}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json._
import play.api.mvc.{Action, Result}
import security.TokenStorage
import services.StripeService
import services.StripeService.ErrorResponse
import slick.driver.PostgresDriver.api._
import scala.collection.JavaConverters._

import scala.concurrent.Future

//noinspection TypeAnnotation
class PaymentCardsController @Inject()(val tokenStorage: TokenStorage,
                                       dbConfigProvider: DatabaseConfigProvider,
                                       stripeService: StripeService) extends BaseController {

  implicit val paymentSourceReads = (__ \ 'source).read[String].map(PaymentSource.apply)
  implicit val cardDtoFormat = Json.format[CardDto]

  val db = dbConfigProvider.get.db

  def addPaymentCard(version: String) = authorized.async(parse.json) { request =>
    processRequestF[PaymentSource](request.body) { dto =>
      val userId = request.token.get.userInfo.id

      def addPaymentSource(stripeId: String) = {
        processStripe(stripeService.getCustomer(stripeId)) { customer =>
          processStripe(stripeService.createCard(customer, dto.source))(_ => Future(success))
        }
      }

      def createNewUser(user: UsersRow) = {
        processStripe(stripeService.createCustomer(dto.source, user.email)) { customer =>
          val userUpdateQuery = for {
            user <- Users if user.id === userId
          } yield (user.stripeId, user.paymentMethod)
          val paymentMethod = user.paymentMethod.getOrElse(customer.getDefaultSource)
          db.run(userUpdateQuery.update(Option(customer.getId), Option(paymentMethod)))
            .map(_ => success)
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

  def removePaymentCard(version: String, id: String) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val userQuery = for {
      user <- Users if user.id === userId && user.stripeId.isDefined
    } yield user

    db.run(userQuery.result.headOption).flatMap { userOpt =>
      userOpt.map { user =>
        processStripe(stripeService.getCustomer(user.stripeId.get)) { customer =>
          processStripe(stripeService.deleteCard(customer, id)) { _ =>
            user.paymentMethod.filter(_.equals(id))
              .map { _ =>
                processStripe(stripeService.getCustomer(user.stripeId.get)) { updatedCustomer =>
                  val userUpdateQuery = for {
                    user <- Users if user.id === userId
                  } yield user.paymentMethod
                  db.run(userUpdateQuery.update(Option(updatedCustomer.getDefaultCard)))
                    .map(_ => success)
                }
              }.getOrElse(Future(success))
          }
        }
      }.getOrElse(Future.successful(badRequest("Payment system doesn't attached")))
    }
  }

  def listPaymentCards(version: String) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val userQuery = for {
      user <- Users if user.id === userId && user.stripeId.isDefined
    } yield user

    db.run(userQuery.result.headOption).flatMap { userOpt =>
      userOpt.map { user =>
        processStripe(stripeService.getCustomer(user.stripeId.get)) { customer =>
          processStripe(stripeService.getCards(customer, 100)) { cards =>
            val dtos = cards.map(_.toDto)
            Future(ok(ListResponse(dtos, 0, 100, dtos.size)))
          }
        }
      }.getOrElse(Future.successful(ok(ListResponse(List.empty[CardDto], 0, 0, 0))))
    }
  }

  def createEphemeralKey(version: String, apiVersion: String) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val stripeQuery = for {
      user <- Users
      if user.id === userId
    } yield user.stripeId
    db.run(stripeQuery.result.headOption)
      .flatMap(result => {
        (for {
          stripeIdOpt <- result
          stripeId <- stripeIdOpt
        } yield stripeId)
          .map(stripeId => processStripe(stripeService.createEphemeralKey(stripeId, apiVersion)) { key =>
            Future(ok(EphemeralKeyDto.create(key)))
          })
          .getOrElse(Future(notFound))
      })
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

  case class EphemeralKeyAssociatedObjectDto(`type`: String,
                                             id: String)

  object EphemeralKeyAssociatedObjectDto {

    implicit val ephemeralKeyAssociatedObjectDtoFormat: Format[EphemeralKeyAssociatedObjectDto] = Json.format[EphemeralKeyAssociatedObjectDto]

    def create(ephemeralKeyAssociatedObject: EphemeralKeyAssociatedObject): EphemeralKeyAssociatedObjectDto = {
      EphemeralKeyAssociatedObjectDto(ephemeralKeyAssociatedObject.getType, ephemeralKeyAssociatedObject.getId)
    }
  }

  case class EphemeralKeyDto(id: String,
                             `object`: String,
                             created: Long,
                             expires: Long,
                             liveMode: Boolean,
                             secret: String,
                             associatedObjects: Seq[EphemeralKeyAssociatedObjectDto])

  object EphemeralKeyDto {

    implicit val ephemeralKeyDtoFormat: Format[EphemeralKeyDto] = Json.format[EphemeralKeyDto]

    def create(key: EphemeralKey): EphemeralKeyDto = EphemeralKeyDto(key.getId, key.getObject, key.getCreated,
      key.getExpires, key.getLivemode, key.getSecret, key.getAssociatedObjects.asScala.map(EphemeralKeyAssociatedObjectDto.create))
  }

}
