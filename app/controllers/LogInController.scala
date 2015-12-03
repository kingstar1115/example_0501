package controllers

import javax.inject.Inject

import controllers.LogInController.EmailLogInDto
import controllers.base.RestResponses
import models.Tables
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsSuccess, JsError, JsPath, Reads}
import play.api.mvc.{BodyParsers, Action, Controller}
import security._
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._
import com.github.t3hnar.bcrypt._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future



class LogInController @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                tokenProvider: TokenProvider,
                                tokenStorage: TokenStorage) extends Controller with RestResponses {

  val db = dbConfigProvider.get[JdbcProfile].db

  def logIn = Action.async(BodyParsers.parse.json) { request =>
    val parseResult = request.body.validate[EmailLogInDto]
    parseResult match {
      case JsError(errors) => Future.successful(validationFailed(JsError.toJson(errors)))

      case JsSuccess(dto, jsPath) =>
        val userQuery = for {
          u <- Tables.Users if u.email === dto.email
        } yield (u.id, u.email, u.firstName, u.lastName, u.verified, u.userType, u.password, u.salt)
        db.run(userQuery.result).map{ result =>
          result.headOption.filter{r =>
            dto.password.bcrypt(r._8.get) == r._7.get
          }.map{ r =>
            val userInfo = UserInfo(r._1, r._2, r._3, r._4, r._5, r._6)
            val token = tokenProvider.generateToken(userInfo)
            tokenStorage.setToken(token)
            ok(TokenKey(token.key))(AuthToken.tokenKeyFormat)
          }.getOrElse(validationFailed("Wrong email or password"))
        }
    }
  }
}

object LogInController {

  case class EmailLogInDto(email: String, password: String)

  implicit val emailLogInDtoReads: Reads[EmailLogInDto] = (
      (JsPath \ "email").read[String](email) and
      (JsPath \ "password").read[String](minLength[String](6) keepAnd maxLength[String](32))
    )(EmailLogInDto.apply _)

}
