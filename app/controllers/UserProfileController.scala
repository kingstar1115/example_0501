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
import models.Tables
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

  val fileSeparator = File.separatorChar
  val picturesFolder = new File(s"${application.path().getPath}${fileSeparator}public${fileSeparator}files")

  def changePassword = authorized.async(BodyParsers.parse.json) { request =>
    val db = dbConfigProvider.get.db
    request.body.validate[PasswordChangeDto] match {
      case JsError(errors) => Future.successful(validationFailed(JsError.toJson(errors)))
      case JsSuccess(dto, p) =>
        val userId = request.token.get.userInfo.id
        val userQuery = for {
          u <- Tables.Users
          if u.id === userId && u.password.isDefined
        } yield u
        db.run(userQuery.result.headOption).flatMap { userOpt =>
          userOpt.map { user =>
            val oldPassword = dto.oldPassword.bcrypt(user.salt)
            oldPassword == user.password.get match {
              case false => Future.successful(validationFailed("Wrong old password"))
              case _ =>
                val newPassword = dto.newPassword.bcrypt(user.salt)
                val updateQuery = Tables.Users.filter(_.id === user.id).map(_.password).update(Some(newPassword))
                db.run(updateQuery).map {
                  case 1 => ok("Success")
                  case _ => badRequest("Can't update password", DatabaseError)
                }
            }
          }.getOrElse(Future.successful(badRequest("Can't find user")))
        }
    }
  }

  def uploadProfilePicture = authorized.async(BodyParsers.parse.multipartFormData) { request =>
    request.body.file("picture").map { requestFile =>
      val userId = request.token.get.userInfo.id
      val tempFileName = s"temp-${requestFile.filename}"
      val tempFile = requestFile.ref.moveTo(new File(picturesFolder, tempFileName))
      Try(ImageIO.read(tempFile)).filter(image => image != null).flatMap(image => Try {
        val extension = tempFile.getName.split("\\.").last
        val newFileName = s"${UUID.randomUUID()}.$extension"
        val newFile = new File(picturesFolder, newFileName)
        Files.copy(tempFile.toPath, newFile.toPath)
        tempFile.delete

        val pictureQuery = Tables.Users.filter(_.id === userId).map(_.profilePicture)
        val updateQuery = Tables.Users.filter(_.id === userId).map(_.profilePicture).update(Some(newFileName))
        val db = dbConfigProvider.get.db
        db.run(pictureQuery.result.head zip updateQuery).map { queryResult =>
          queryResult._1.map { oldFileName =>
            val oldFile = new File(picturesFolder, oldFileName)
            oldFile.delete()
          }
          ok(newFileName)
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

  implicit val passwordChangeDtoReads: Reads[PasswordChangeDto] = (
    (JsPath \ "oldPassword").read[String](minLength[String](6) keepAnd maxLength[String](32)) and
    (JsPath \ "newPassword").read[String](minLength[String](6) keepAnd maxLength[String](32))
    )(PasswordChangeDto.apply _)
}
