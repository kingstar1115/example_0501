package controllers

import javax.inject.Inject

import com.github.t3hnar.bcrypt._
import commons.enums.FacebookError
import controllers.LogInController.{EmailLogInDto, FacebookLogInDto}
import controllers.base._
import models.Tables
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsError, JsPath, JsSuccess, Reads}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, BodyParsers}
import security._
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class LogInController @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                tokenProvider: TokenProvider,
                                val tokenStorage: TokenStorage,
                                val ws: WSClient)
  extends BaseController() with FacebookCalls {

  val db = dbConfigProvider.get.db

  def logIn = Action.async(BodyParsers.parse.json) { request =>
    val parseResult = request.body.validate[EmailLogInDto]
    parseResult match {
      case JsError(errors) => Future.successful(validationFailed(JsError.toJson(errors)))

      case JsSuccess(dto, jsPath) =>
        val userQuery = for {
          u <- Tables.Users if u.email === dto.email
        } yield (u.id, u.email, u.firstName, u.lastName, u.verified, u.userType, u.password, u.salt)
        db.run(userQuery.result).map { result =>
          result.headOption.filter { r =>
            r._8.isDefined && dto.password.bcrypt(r._8.get) == r._7.get
          }.map { r =>
            val userInfo = UserInfo(r._1, r._2, r._3, r._4, r._5, r._6)
            tokenOkResponse(userInfo)
          }.getOrElse(validationFailed("Wrong email or password"))
        }
    }
  }

  def logOut = authorized { request =>
    tokenStorage.deleteToken(request.token.get)
    ok("Success log out")
  }

  def facebookLogIn = Action.async(BodyParsers.parse.json) { request =>
    val parseResult = request.body.validate[FacebookLogInDto]
    parseResult match {
      case JsError(errors) => Future.successful(validationFailed(JsError.toJson(errors)))

      case JsSuccess(dto, jsPath) =>
        facebookMe(dto.token).flatMap { wsResponse =>
          wsResponse.status match {
            case 200 =>
              wsResponse.json.validate[FacebookResponseDto] match {
                case JsError(e) => Future.successful(badRequest("Invalid token", FacebookError))

                case JsSuccess(facebookDto, p) =>
                  val userQuery = for {
                    u <- Tables.Users if u.facebookId.isDefined && u.facebookId === facebookDto.id
                  } yield (u.id, u.email, u.firstName, u.lastName, u.verified, u.userType)
                  db.run(userQuery.take(1).result).map { resultSet =>
                    resultSet.headOption.map { r =>
                      val userInfo = UserInfo(r._1, r._2, r._3, r._4, r._5, r._6)
                      tokenOkResponse(userInfo)
                    }.getOrElse(validationFailed("Can't find user"))
                  }
              }

            case _ => Future.successful(badRequest(s"Failed to fetch data. Code - ${wsResponse.status}", FacebookError))
          }
        }
    }
  }

  private def tokenOkResponse(userInfo: UserInfo) = {
    val token = tokenProvider.generateToken(userInfo)
    tokenStorage.setToken(token)
    ok(AuthResponse(token.key, userInfo.firstName, userInfo.lastName,
      userInfo.userType, userInfo.verified))(AuthToken.authResponseFormat)
  }
}

object LogInController {

  case class EmailLogInDto(email: String, password: String)

  case class FacebookLogInDto(token: String)

  implicit val emailLogInDtoReads: Reads[EmailLogInDto] = (
      (JsPath \ "email").read[String](email) and
      (JsPath \ "password").read[String](minLength[String](6) keepAnd maxLength[String](32))
    )(EmailLogInDto.apply _)

  implicit val facebookLogInDtoReads: Reads[FacebookLogInDto] = (JsPath \ "token").read[String].map(FacebookLogInDto.apply)

}
