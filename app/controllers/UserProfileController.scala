package controllers

import java.io.File
import java.util.{NoSuchElementException, UUID}
import javax.imageio.ImageIO
import javax.inject.Inject

import com.github.t3hnar.bcrypt._
import commons.enums.{DatabaseError, PaymentMethods}
import controllers.UserProfileController._
import controllers.base.BaseController
import models.Tables._
import play.api.Logger
import play.api.data.validation.ValidationError
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.{Action, BodyParsers, Result}
import security.TokenStorage
import services.StripeService.ErrorResponse
import services.{FileService, StripeService}
import slick.dbio.Effect.Write
import slick.driver.PostgresDriver.api._
import slick.profile.FixedSqlAction

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


class UserProfileController @Inject()(val tokenStorage: TokenStorage,
                                      dbConfigProvider: DatabaseConfigProvider,
                                      application: play.Application,
                                      fileService: FileService,
                                      stripeService: StripeService) extends BaseController {

  implicit val passwordChangeDtoReads: Reads[PasswordChangeDto] = (
    (JsPath \ "oldPassword").read[String](minLength[String](6) keepAnd maxLength[String](32)) and
      (JsPath \ "newPassword").read[String](minLength[String](6) keepAnd maxLength[String](32))
    ) (PasswordChangeDto.apply _)
  implicit val userProfileDtoWrites = Json.writes[UserProfileDto]
  implicit val userProfileUpdateReads: Reads[UserUpdateDto] = (
    (JsPath \ "firstName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "lastName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "phoneCountryCode").read[String](pattern("[0-9]{1,4}".r, "Invalid country code")) and
      (JsPath \ "phoneNumber").read[String](pattern("[0-9]{8,14}".r, "Invalid phone format")) and
      (JsPath \ "email").read[String](email)
    ) (UserUpdateDto.apply _)
  implicit val paymentSourceReads = (__ \ 'method).read[String].map(PaymentMethod.apply)

  val fileSeparator = File.separatorChar
  val picturesFolder = fileService.getFolder("pictures")
  val db = dbConfigProvider.get.db

  def getProfileInfo = authorized.async { request =>
    val userId = request.token.get.userInfo.id

    val userQuery = for {u <- Users if u.id === userId} yield u
    db.run(userQuery.result.head).map { user =>
      val dto = UserProfileDto(user.firstName, user.lastName, user.phoneCode.concat(user.phone),
        user.email, user.profilePicture, user.userType, user.verified, user.paymentMethod)
      ok(dto)
    }
  }

  def changePassword = authorized.async(BodyParsers.parse.json) { request =>
    processRequest[PasswordChangeDto](request.body) { dto =>
      val userId = request.token.get.userInfo.id
      val userQuery = for {
        u <- Users
        if u.id === userId && u.password.isDefined
      } yield u

      db.run(userQuery.result.headOption).flatMap { userOpt =>
        userOpt.map { user =>
          val oldPassword = dto.oldPassword.bcrypt(user.salt)
          oldPassword == user.password.get match {
            case false => Future.successful(validationFailed("Wrong old password"))
            case _ =>
              val newPassword = dto.newPassword.bcrypt(user.salt)
              val updateQuery = Users.filter(_.id === user.id).map(_.password).update(Some(newPassword))
              db.run(updateQuery).map {
                case 1 => ok("Success")
                case _ => badRequest("Can't update password", DatabaseError)
              }
          }
        }.getOrElse(Future.successful(badRequest("Can't find user")))
      }
    }
  }

  def uploadProfilePicture = authorized.async(parse.multipartFormData) { implicit request =>
    request.body.file("picture").map { requestFile =>
      val token = request.token.get
      val userId = token.userInfo.id
      val tempFileName = s"temp-${requestFile.filename}"
      val tempFile = requestFile.ref.moveTo(new File(picturesFolder, tempFileName))
      Try(ImageIO.read(tempFile)).filter(image => image != null).flatMap(image => Try {
        val extension = fileService.getFileExtension(tempFile)
        val newFileName = s"${UUID.randomUUID()}.$extension"
        val newFile = new File(picturesFolder, newFileName)
        fileService.moveFile(tempFile, newFile)

        val newPictureUrl = routes.UserProfileController.getProfilePicture(newFileName).absoluteURL()
        val pictureQuery = Users.filter(_.id === userId).map(_.profilePicture)
        val updateQuery = Users.filter(_.id === userId).map(_.profilePicture).update(Some(newPictureUrl))

        db.run(pictureQuery.result.head zip updateQuery).map { queryResult =>
          queryResult._1.map { oldPictureUrl =>
            val oldFileName = oldPictureUrl.split("/").last
            val oldFile = new File(picturesFolder, oldFileName)
            if (oldFile.exists()) {
              oldFile.delete()
            }
          }
          tokenStorage.updateToken(token.copy(userInfo = token.userInfo.copy(picture = Some(newPictureUrl))))
          ok(newPictureUrl)
        }
      }) match {
        case Success(result) => result
        case Failure(e) =>
          Logger.debug("Failed to update profile picture", e)
          Future.successful(badRequest(e.getMessage))
      }
    }.getOrElse(Future.successful(badRequest("Field picture required")))
  }

  def getProfilePicture(fileName: String) = Action { request =>
    Ok.sendFile(new File(picturesFolder, fileName))
  }

  def updateProfile() = authorized.async(parse.json) { request =>
    processRequest[UserUpdateDto](request.body) { dto =>
      val userId = request.token.get.userInfo.id
      val updateQuery = Users.filter(user => user.id === userId && user.verified === true)
        .map(user => (user.firstName, user.lastName, user.phoneCode, user.phone, user.email))
        .update(dto.firstName, dto.lastName, dto.phoneCode, dto.phone, dto.email)

      db.run(updateQuery zip getUserSelectQuery(userId).result.head)
        .map(processProfileUpdate(_, "Can’t update not verified user profile"))
    }
  }

  def updateDefaultPaymentMethod() = authorized.async(parse.json) { request =>
    processRequest[PaymentMethod](request.body) { dto =>
      val userId = request.token.get.userInfo.id
      val paymentMethodOpt = Try(PaymentMethods.withName(dto.name))
        .map(paymentMethod => Future(Option(paymentMethod.toString)))
        .getOrElse {
          val userSelectQuery = for {
            user <- Users if user.id === userId && user.stripeId.isDefined
          } yield user
          db.run(userSelectQuery.result.headOption).flatMap(_.map { user =>
            processStripe(stripeService.getCustomer(user.stripeId.get)) { customer =>
              processStripe(stripeService.getCard(customer, dto.name))(card => Future(Option(card.getId)))
            }
          }.getOrElse(Future(None)))
        }
      paymentMethodOpt.flatMap(_.map { paymentMethod =>
        val updateQuery = Users.filter(user => user.id === userId && user.verified === true)
          .map(_.paymentMethod)
          .update(Option(paymentMethod))

        db.run(updateQuery zip getUserSelectQuery(userId).result.head)
          .map(processProfileUpdate(_, "Can’t update user default payment method"))
      }.getOrElse(Future(badRequest("Invalid payment method"))))
    }
  }

  def processProfileUpdate(updateResult: (Int, UsersRow), errorMessage: String) = {
    updateResult._1 match {
      case 1 =>
        val user = updateResult._2
        val dto = UserProfileDto(user.firstName, user.lastName, user.phoneCode.concat(user.phone),
          user.email, user.profilePicture, user.userType, user.verified, user.paymentMethod)
        ok(dto)
      case _ => badRequest(errorMessage)
    }
  }

  def getUserSelectQuery(id: Int) = {
    for {u <- Users if u.id === id} yield u
  }

  def processStripe[T](result: Future[Either[ErrorResponse, T]])(f: T => Future[Option[String]]) = {
    result.flatMap {
      case Left(error) => Future(None)
      case Right(data) => f(data)
    }
  }
}

object UserProfileController {

  case class PasswordChangeDto(oldPassword: String,
                               newPassword: String)

  case class UserProfileDto(firstName: String,
                            lastName: String,
                            phone: String,
                            email: String,
                            picture: Option[String],
                            userType: Int,
                            verified: Boolean,
                            paymentMethod: Option[String])

  case class UserUpdateDto(firstName: String,
                           lastName: String,
                           phoneCode: String,
                           phone: String,
                           email: String)

  case class PaymentMethod(name: String)

}
