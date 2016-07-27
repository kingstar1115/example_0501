package controllers

import javax.inject.Inject

import com.github.t3hnar.bcrypt._
import commons.enums.{AuthyError, DatabaseError, FacebookError}
import controllers.SignUpController.{EmailSignUpDto, FBSighUpResponseDto, FacebookSighUpDto, FbTokenDto}
import controllers.base.FacebookCalls.FacebookResponseDto
import controllers.base.{BaseController, FacebookCalls}
import models.Tables
import models.Tables._
import play.api.data.validation.ValidationError
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, BodyParsers, Result}
import security._
import services.{AuthyVerifyService, EmailService}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}


class SignUpController @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                 tokenProvider: TokenProvider,
                                 val tokenStorage: TokenStorage,
                                 val ws: WSClient,
                                 verifyService: AuthyVerifyService,
                                 emailService: EmailService)
  extends BaseController() with FacebookCalls {

  implicit val fbSighUpResponseDtoFormat = Json.format[FBSighUpResponseDto]

  val db = dbConfigProvider.get.db

  def emailSignUp = Action.async(BodyParsers.parse.json) { implicit request =>

    def onValidationFailed(errors: Seq[(JsPath, Seq[ValidationError])]) =
      Future.successful(validationFailed(JsError.toJson(errors)))

    def onValidationPassed(dto: EmailSignUpDto) = {
      val existsQuery = for {
        user <- Users if user.email === dto.email || (user.phoneCode === dto.phoneCode && user.phone === dto.phone)
      } yield user
      db.run(existsQuery.length.result)
        .filter(_ == 0)
        .flatMap { count =>
          verifyService.sendVerifyCode(dto.phoneCode.toInt, dto.phone).flatMap {
            case verifyResult if verifyResult.success =>
              val user = emailSighUpUser(dto)
              val insertQuery = Users.map(u => (u.firstName, u.lastName, u.email, u.phoneCode, u.phone, u.salt,
                u.password, u.userType)) returning Users.map(_.id)

              db.run((insertQuery += user).asTry).map {
                case Success(insertResult) =>
                  val token = getToken(insertResult, (user._1, user._2, user._3, 0))
                  emailService.sendUserRegistredEmail(dto.firstName, dto.lastName, dto.email)
                  ok(AuthResponse(token.key, token.userInfo.firstName, token.userInfo.lastName,
                    0, token.userInfo.verified, None, dto.phoneCode.concat(dto.phone)))(AuthToken.authResponseFormat)

                case Failure(e) => badRequest(e.getMessage, DatabaseError)
              }
            case other => Future.successful(badRequest(other.message, AuthyError))
          }
        }.recover {
        case _ => validationFailed("User with this email or phone already exists")
      }
    }

    request.body.validate[EmailSignUpDto].fold(onValidationFailed, onValidationPassed)
  }

  def fbAuth = Action.async(BodyParsers.parse.json) { request =>

    def onValidationFailed(errors: Seq[(JsPath, Seq[ValidationError])]) =
      wrapInFuture(badRequest("Token required"))

    def onValidationPassed(dto: FbTokenDto) = {
      facebookMe(dto.token) flatMap { wsResponse =>

        def onError(errors: Seq[(JsPath, Seq[ValidationError])]) =
          wrapInFuture(badRequest("Failed to get FB information"))

        def onSuccess(fbDto: FacebookResponseDto) = {
          val userQuery = for {
            u <- Users if u.facebookId === fbDto.id
          } yield u

          def onUserExists(user: Tables.UsersRow) = {
            val key = getToken(user.id, (user.firstName, user.lastName, user.email, 1)).key
            val responseDto = AuthResponse(key, user.firstName, user.lastName, 1, user.verified, user.profilePicture,
              user.phoneCode.concat(user.phone))
            ok(responseDto)(AuthToken.authResponseFormat)
          }

          def onUserNotExists = {
            val responseDto = new FBSighUpResponseDto(fbDto.firstName, fbDto.lastName, fbDto.email)
            ok(responseDto)
          }

          db.run(userQuery.result.headOption).map(_.map(onUserExists).getOrElse(onUserNotExists))
        }

        wsResponse.json.validate[FacebookResponseDto].fold(onError, onSuccess)
      }
    }

    request.body.validate[FbTokenDto].fold(onValidationFailed, onValidationPassed)
  }

  def fbSignUp = Action.async(BodyParsers.parse.json) { implicit request =>
    def onValidationSuccess(dto: FacebookSighUpDto) = {
      facebookMe(dto.token).flatMap { wsResponse =>
        wsResponse.status match {
          case 200 =>
            def onFBSuccess(fbDto: FacebookResponseDto) = {

              def createUser: Future[Result] = {
                verifyService.sendVerifyCode(dto.phoneCode.toInt, dto.phone).flatMap {
                  case response if response.success =>
                    val insertQuery = (Users.map(u => (u.firstName, u.lastName, u.email, u.phoneCode, u.phone,
                      u.facebookId, u.userType, u.salt, u.profilePicture)) returning Users.map(_.id)) +=(dto.firstName,
                      dto.lastName, dto.email, dto.phoneCode, dto.phone, Some(fbDto.id), 1,
                      generateSalt, Option(fbDto.picture.data.url))
                    db.run(insertQuery).map { userId =>
                      emailService.sendUserRegistredEmail(dto.firstName, dto.lastName, dto.email)
                      val key = getToken(userId, (dto.firstName, dto.lastName, dto.email, 1)).key
                      ok(AuthResponse(key, dto.firstName, dto.lastName, 1, false,
                        Option(fbDto.picture.data.url), dto.phoneCode.concat(dto.phone)))(AuthToken.authResponseFormat)
                    }

                  case failed => wrapInFuture(badRequest(failed.message, AuthyError))
                }
              }

              val existsQuery = for {
                u <- Users if u.facebookId === fbDto.id || u.email === dto.email || (u.phoneCode === dto.phoneCode
                && u.phone === dto.phone)
              } yield u

              db.run(existsQuery.length.result).flatMap {
                case 0 => createUser
                case _ => wrapInFuture(validationFailed("User with this FB id or Email Or Phone already exists"))
              }
            }

            wsResponse.json.validate[FacebookResponseDto]
              .fold(errors => wrapInFuture(badRequest("Invalid token", FacebookError)), onFBSuccess)

          case _ => wrapInFuture(badRequest(s"Failed to fetch FB data", FacebookError))
        }
      }
    }

    request.body.validate[FacebookSighUpDto].fold(jsonValidationFailedF, onValidationSuccess)
  }

  private def getToken(id: Integer,
                       user: (String, String, String, Int)) = {
    val userInfo = UserInfo(id, user._3, user._1, user._2, verified = false, user._4, None)
    val token = tokenProvider.generateToken(userInfo)
    tokenStorage.setToken(token)
  }

  private def emailSighUpUser(dto: EmailSignUpDto) = {
    val salt = generateSalt
    val hashedPassword = dto.password.bcrypt(salt)
    (dto.firstName, dto.lastName, dto.email, dto.phoneCode, dto.phone, salt, Option(hashedPassword), 0)
  }
}

object SignUpController {


  case class EmailSignUpDto(firstName: String,
                            lastName: String,
                            phoneCode: String,
                            phone: String,
                            email: String,
                            password: String)

  case class FacebookSighUpDto(firstName: String,
                               lastName: String,
                               phoneCode: String,
                               phone: String,
                               email: String,
                               token: String)

  case class FbTokenDto(token: String)

  case class FBSighUpResponseDto(firstName: String,
                                 lastName: String,
                                 email: Option[String])

  implicit val emailSignUpDtoReads: Reads[EmailSignUpDto] = (
    (JsPath \ "firstName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "lastName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "phoneCountryCode").read[String](pattern("[0-9]{1,4}".r, "Invalid country code")) and
      (JsPath \ "phoneNumber").read[String](pattern("[0-9]{8,14}".r, "Invalid phone format")) and
      (JsPath \ "email").read[String](email) and
      (JsPath \ "password").read[String](minLength[String](6) keepAnd maxLength[String](32))
    ) (EmailSignUpDto.apply _)

  implicit val facebookSighUpDtoReads: Reads[FacebookSighUpDto] = (
    (JsPath \ "firstName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "lastName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "phoneCountryCode").read[String](pattern("[0-9]{1,4}".r, "Invalid country code")) and
      (JsPath \ "phoneNumber").read[String](pattern("[0-9]{8,14}".r, "Invalid phone format")) and
      (JsPath \ "email").read[String](email) and
      (JsPath \ "token").read[String](minLength[String](10))
    ) (FacebookSighUpDto.apply _)

  implicit val fbTokenDtoReads: Reads[FbTokenDto] = (__ \ 'token).read[String].map(FbTokenDto)
}
