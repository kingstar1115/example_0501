package controllers

import javax.inject.Inject

import com.github.t3hnar.bcrypt._
import commons.enums.DatabaseError
import controllers.UserProfileController._
import controllers.base.BaseController
import models.Tables
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.BodyParsers
import security.TokenStorage
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class UserProfileController @Inject()(val tokenStorage: TokenStorage,
                                      dbConfigProvider: DatabaseConfigProvider) extends BaseController {

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
}

object UserProfileController {

  case class PasswordChangeDto(oldPassword: String,
                               newPassword: String)

  implicit val passwordChangeDtoReads: Reads[PasswordChangeDto] = (
    (JsPath \ "oldPassword").read[String](minLength[String](6) keepAnd maxLength[String](32)) and
    (JsPath \ "newPassword").read[String](minLength[String](6) keepAnd maxLength[String](32))
    )(PasswordChangeDto.apply _)
}
