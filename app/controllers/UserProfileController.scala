package controllers

import java.io.File
import java.nio.file.Files
import java.util.UUID
import javax.imageio.ImageIO
import javax.inject.Inject

import com.github.t3hnar.bcrypt._
import commons.enums.DatabaseError
import controllers.UserProfileController._
import controllers.base.BaseController
import models.Tables._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.{Action, BodyParsers}
import security.TokenStorage
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


class UserProfileController @Inject()(val tokenStorage: TokenStorage,
                                      dbConfigProvider: DatabaseConfigProvider,
                                      application: play.Application) extends BaseController {

  implicit val passwordChangeDtoReads: Reads[PasswordChangeDto] = (
    (JsPath \ "oldPassword").read[String](minLength[String](6) keepAnd maxLength[String](32)) and
    (JsPath \ "newPassword").read[String](minLength[String](6) keepAnd maxLength[String](32))
    )(PasswordChangeDto.apply _)
  implicit val userProfileDtoWrites = Json.writes[UserProfileDto]

  val fileSeparator = File.separatorChar
  val picturesFolder = new File(s"${application.path().getPath}${fileSeparator}pictures")

  def getProfileInfo = authorized.async { request =>
    val db = dbConfigProvider.get.db
    val userId = request.token.get.userInfo.id
    val userQuery = for {u <- Users if u.id === userId} yield u
    db.run(userQuery.result.head).map { user =>
      val dto = UserProfileDto(user.firstName, user.lastName, user.phoneCode.concat(user.phone),
        user.email, user.profilePicture, user.userType, user.verified)
      ok(dto)
    }
  }

  def changePassword = authorized.async(BodyParsers.parse.json) { request =>
    val db = dbConfigProvider.get.db
    request.body.validate[PasswordChangeDto] match {
      case JsError(errors) => Future.successful(validationFailed(JsError.toJson(errors)))
      case JsSuccess(dto, p) =>
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
      if (!picturesFolder.exists()) {
        Logger.info("Creating files directory")
        val isCreated = picturesFolder.mkdir()
        Logger.info(s"File directory created - $isCreated")
      }
      val tempFile = requestFile.ref.moveTo(new File(picturesFolder, tempFileName))
      Try(ImageIO.read(tempFile)).filter(image => image != null).flatMap(image => Try {
        val extension = tempFile.getName.split("\\.").last
        val newFileName = s"${UUID.randomUUID()}.$extension"
        val newFile = new File(picturesFolder, newFileName)
        Files.copy(tempFile.toPath, newFile.toPath)
        tempFile.delete

        val newPictureUrl = routes.UserProfileController.getProfilePicture(newFileName).absoluteURL()
        val pictureQuery = Users.filter(_.id === userId).map(_.profilePicture)
        val updateQuery = Users.filter(_.id === userId).map(_.profilePicture).update(Some(newPictureUrl))
        val db = dbConfigProvider.get.db
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
}

object UserProfileController {

  case class PasswordChangeDto(oldPassword: String,
                               newPassword: String)

  case class UserProfileDto(firstName: String,
                            lastName: String,
                            phone: String,
                            email: Option[String],
                            picture: Option[String],
                            userType: Int,
                            verified: Boolean)

}
