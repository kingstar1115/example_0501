package controllers.rest

import java.util.NoSuchElementException

import com.github.t3hnar.bcrypt._
import commons.ServerError
import commons.enums.{AuthyError, DatabaseError}
import commons.monads.transformers.EitherT
import controllers.rest.SignUpController._
import controllers.rest.base.FacebookCalls.FacebookProfile
import controllers.rest.base._
import javax.inject.Inject
import models.Tables._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, BodyParsers, RequestHeader}
import security._
import services.AuthyVerifyService.AuthyResponseDto
import services.{AuthyVerifyService, EmailService, StripeService}
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future
import scala.util.{Failure, Success}

//noinspection TypeAnnotation
class SignUpController @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                                 tokenProvider: TokenProvider,
                                 val tokenStorage: TokenStorage,
                                 val ws: WSClient,
                                 verifyService: AuthyVerifyService,
                                 emailService: EmailService,
                                 stripeService: StripeService)
  extends BaseController() with FacebookCalls with HasDatabaseConfigProvider[JdbcProfile] {

  def emailSignUp(version: String) = Action.async(BodyParsers.parse.json) { implicit request =>

    processRequestF[EmailSignUpDto](request.body) { dto =>
      (for {
        count <- db.run(Users.filter(user => user.email === dto.email || (user.phoneCode === dto.phoneCode && user.phone === dto.phone)).length.result) if count == 0
        verifyResponse <- verifyService.sendVerifyCode(dto.phoneCode.toInt, dto.phone)
      } yield verifyResponse).flatMap({
        case verifyResult if verifyResult.success =>
          saveUser(dto)
        case other =>
          Future(badRequest(other.message, AuthyError))
      }).recover {
        case n: NoSuchElementException =>
          badRequest("User with such email or phone already exists")
      }
    }
  }

  private def saveUser(signUpDto: EmailSignUpDto)(implicit requestHeader: RequestHeader) = {
    val salt = generateSalt
    val hashedPassword = signUpDto.password.bcrypt(salt)

    val insert = for {
      customer <- DBIO.from(stripeService.createCustomer(signUpDto.email))
      userId <- Users.map(u => (u.firstName, u.lastName, u.email, u.phoneCode, u.phone, u.salt,
        u.password, u.userType, u.stripeId)) returning Users.map(_.id) += (signUpDto.firstName, signUpDto.lastName, signUpDto.email,
        signUpDto.phoneCode, signUpDto.phone, salt, Option(hashedPassword), EmailUserType, Option(customer.getId))
    } yield userId

    db.run(insert.asTry).map {
      case Success(userId) =>
        val token = getToken(userId, (signUpDto.firstName, signUpDto.lastName, signUpDto.email, EmailUserType))
        emailService.sendUserRegisteredEmail(signUpDto.firstName, signUpDto.lastName, signUpDto.email)

        ok(AuthResponse(token.key, token.userInfo.firstName, token.userInfo.lastName,
          EmailUserType, token.userInfo.verified, None, signUpDto.phoneCode.concat(signUpDto.phone)))(AuthToken.authResponseFormat)
      case Failure(e) =>
        badRequest(e.getMessage, DatabaseError)
    }
  }

  def fbAuth(version: String) = Action.async(BodyParsers.parse.json) { request =>

    def loadUser(fbDto: FacebookProfile) = {
      val userQuery = for {
        user <- Users if user.facebookId === fbDto.id
      } yield user
      db.run(userQuery.result.headOption).map {
        case Some(user) =>
          val tokenKey = getToken(user.id, (user.firstName, user.lastName, user.email, FacebookUserType)).key
          val responseDto = AuthResponse(tokenKey, user.firstName, user.lastName, FacebookUserType, user.verified, user.profilePicture,
            user.phoneCode.concat(user.phone))
          ok(responseDto)(AuthToken.authResponseFormat)

        case None =>
          val responseDto = FBSighUpResponseDto(fbDto.firstName, fbDto.lastName, fbDto.email)
          ok(responseDto)
      }
    }

    processRequestF[FbTokenDto](request.body) { dto =>
      facebookMe(dto.token).flatMap {
        case Right(facebookProfile) =>
          loadUser(facebookProfile)
        case Left(_) =>
          Future(badRequest("Failed to load Facebook profile"))
      }
    }
  }

  def fbSignUp(version: String) = Action.async(BodyParsers.parse.json) { implicit request =>
    processRequestF[FacebookSighUpDto](request.body) { implicit dto =>
      (for {
        facebookProfile <- EitherT(facebookMe(dto.token))
        _ <- EitherT(verifyPhone(dto.phoneCode, dto.phone))
        _ <- EitherT(checkUserNotExists(facebookProfile))
      } yield facebookProfile).inner.flatMap {
        case Right(facebookProfile) =>
          saveFaceBookUser(facebookProfile)
        case Left(error) =>
          Future(badRequest(error.message))
      }
    }
  }

  private def verifyPhone(phoneCode: String, phone: String): Future[Either[ServerError, AuthyResponseDto]] = {
    verifyService.sendVerifyCode(phoneCode.toInt, phoneCode).map {
      case response if response.success =>
        Right(response)
      case failed =>
        Left(ServerError(failed.message, Option(AuthyError)))
    }
  }

  private def checkUserNotExists(fbUser: FacebookProfile)(implicit sighUpDto: FacebookSighUpDto): Future[Either[ServerError, Unit]] = {
    val existsQuery = for {
      u <- Users if u.facebookId === fbUser.id || u.email === sighUpDto.email || (u.phoneCode === sighUpDto.phoneCode
        && u.phone === sighUpDto.phone)
    } yield u

    db.run(existsQuery.length.result).map {
      case 0 =>
        Right((): Unit)
      case _ =>
        Left(ServerError("User with this FB id or Email or Phone already exists"))
    }
  }

  private def saveFaceBookUser(fbUser: FacebookProfile)(implicit sighUpDto: FacebookSighUpDto, requestHeader: RequestHeader) = {
    val saveUser = for {
      stripeCustomer <- DBIO.from(stripeService.createCustomer(sighUpDto.email))
      userId <- (Users.map(u => (u.firstName, u.lastName, u.email, u.phoneCode, u.phone,
        u.facebookId, u.userType, u.salt, u.profilePicture, u.stripeId)) returning Users.map(_.id)) += (sighUpDto.firstName,
        sighUpDto.lastName, sighUpDto.email, sighUpDto.phoneCode, sighUpDto.phone, Some(fbUser.id), FacebookUserType,
        generateSalt, Option(fbUser.picture.data.url), Option(stripeCustomer.getId))
    } yield userId

    db.run(saveUser.asTry).map {
      case Success(userId) =>
        emailService.sendUserRegisteredEmail(sighUpDto.firstName, sighUpDto.lastName, sighUpDto.email)
        val tokenKey = getToken(userId, (sighUpDto.firstName, sighUpDto.lastName, sighUpDto.email, FacebookUserType)).key

        ok(AuthResponse(tokenKey, sighUpDto.firstName, sighUpDto.lastName, FacebookUserType, verified = false,
          Option(fbUser.picture.data.url), sighUpDto.phoneCode.concat(sighUpDto.phone)))(AuthToken.authResponseFormat)

      case Failure(e) =>
        badRequest(e.getMessage, DatabaseError)
    }
  }

  private def getToken(id: Integer,
                       user: (String, String, String, Int)): AuthToken = {
    val userInfo = UserInfo(id, user._3, user._1, user._2, verified = false, user._4, None)
    val token = tokenProvider.generateToken(userInfo)
    tokenStorage.setToken(token)
  }
}

object SignUpController {

  val EmailUserType: Int = 0
  val FacebookUserType: Int = 1

  case class EmailSignUpDto(firstName: String,
                            lastName: String,
                            phoneCode: String,
                            phone: String,
                            email: String,
                            password: String)

  object EmailSignUpDto {
    implicit val jsonReads: Reads[EmailSignUpDto] = (
      (JsPath \ "firstName").read[String](maxLength[String](150)) and
        (JsPath \ "lastName").read[String](maxLength[String](150)) and
        (JsPath \ "phoneCountryCode").read[String](pattern("[0-9]{1,4}".r, "Invalid country code")) and
        (JsPath \ "phoneNumber").read[String](pattern("[0-9]{8,14}".r, "Invalid phone format")) and
        (JsPath \ "email").read[String](email) and
        (JsPath \ "password").read[String](minLength[String](6) keepAnd maxLength[String](32))
      ) (EmailSignUpDto.apply _)
  }

  case class FacebookSighUpDto(firstName: String,
                               lastName: String,
                               phoneCode: String,
                               phone: String,
                               email: String,
                               token: String)

  object FacebookSighUpDto {
    implicit val facebookSighUpDtoReads: Reads[FacebookSighUpDto] = (
      (JsPath \ "firstName").read[String](maxLength[String](150)) and
        (JsPath \ "lastName").read[String](maxLength[String](150)) and
        (JsPath \ "phoneCountryCode").read[String](pattern("[0-9]{1,4}".r, "Invalid country code")) and
        (JsPath \ "phoneNumber").read[String](pattern("[0-9]{8,14}".r, "Invalid phone format")) and
        (JsPath \ "email").read[String](email) and
        (JsPath \ "token").read[String](minLength[String](10))
      ) (FacebookSighUpDto.apply _)
  }

  case class FbTokenDto(token: String)

  object FbTokenDto {
    implicit val jsonRead: Reads[FbTokenDto] = (__ \ 'token).read[String]
      .map(token => FbTokenDto(token))
  }

  case class FBSighUpResponseDto(firstName: String,
                                 lastName: String,
                                 email: Option[String])

  object FBSighUpResponseDto {
    implicit val jsonFormat: Format[FBSighUpResponseDto] = Json.format[FBSighUpResponseDto]
  }
}
