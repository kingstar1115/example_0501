package controllers

import javax.inject.Inject

import com.github.t3hnar.bcrypt._
import commons.enums.CommonError
import controllers.LogInController.{EmailLogInDto, ForgotPasswordDto}
import controllers.SignUpController.FbTokenDto
import controllers.base._
import models.Tables
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, Reads}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, BodyParsers, RequestHeader}
import security._
import services.EmailService
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class LogInController @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                tokenProvider: TokenProvider,
                                val tokenStorage: TokenStorage,
                                val ws: WSClient,
                                mailService: EmailService)
  extends BaseController() with FacebookCalls {

  val db = dbConfigProvider.get.db

  def logIn(version: String) = Action.async(BodyParsers.parse.json) { request =>
    processRequestF[EmailLogInDto](request.body) { dto =>
      val userQuery = for {
        u <- Tables.Users if u.email === dto.email && u.userType === 0
      } yield u
      db.run(userQuery.result).map { result =>
        result.headOption.filter(r => dto.password.bcrypt(r.salt) == r.password.get)
          .map { user =>
            val userInfo = UserInfo(user.id, user.email, user.firstName, user.lastName,
              user.verified, user.userType, user.profilePicture)
            val token = tokenProvider.generateToken(userInfo)
            tokenStorage.setToken(token)

            ok(AuthResponse(token.key, user.firstName, user.lastName, user.userType, user.verified,
              user.profilePicture, user.phoneCode.concat(user.phone)))(AuthToken.authResponseFormat)
          }.getOrElse(validationFailed("Wrong email or password"))
      }
    }
  }

  def logOut(version: String) = authorized { request =>
    tokenStorage.deleteToken(request.token.get)
    ok("Success log out")
  }

  def forgotPassword(version: String) = Action.async(BodyParsers.parse.json) { implicit request =>
    version match {
      case "v1" =>
        processRequestF[FbTokenDto](request.body) { dto =>
          forgotPasswordInternal(dto.token)
        }
      case "v2" =>
        processRequestF[ForgotPasswordDto](request.body) { dto =>
          forgotPasswordInternal(dto.email)
        }
    }
  }

  private def forgotPasswordInternal(email: String)(implicit request: RequestHeader) = {
    val userQuery = for {u <- Users if u.email === email} yield u
    db.run(userQuery.result.headOption)
      .map(userOpt => userOpt.map(Right(_)).getOrElse(Left(validationFailed("User not found"))))
      .flatMap {
        case Left(error) => Future.successful(error)
        case Right(user) =>
          val code = tokenProvider.generateKey
          val recoverURL = routes.PasswordRecoveryController.getRecoverPasswordPage(code).absoluteURL()
          mailService.sendPasswordForgetEmail(user.email, recoverURL)
          db.run(Users.filter(_.id === user.id).map(_.code).update(Option(code))).map {
            case 1 => ok("Instruction send to specified email")
            case _ => badRequest("Oops, something went wrong", CommonError)
          }
      }
  }
}

object LogInController {

  case class EmailLogInDto(email: String, password: String)

  case class FacebookLogInDto(token: String)

  case class ForgotPasswordDto(email: String)

  implicit val emailLogInDtoReads: Reads[EmailLogInDto] = (
    (JsPath \ "email").read[String](email) and
      (JsPath \ "password").read[String](minLength[String](6) keepAnd maxLength[String](32))
    ) (EmailLogInDto.apply _)

  implicit val facebookLogInDtoReads: Reads[FacebookLogInDto] = (JsPath \ "token").read[String].map(FacebookLogInDto.apply)

  implicit val forgotPasswordDtoReads: Reads[ForgotPasswordDto] = (JsPath \ 'email).read[String].map(ForgotPasswordDto)

}
