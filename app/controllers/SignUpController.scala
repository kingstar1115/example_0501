package controllers

import javax.inject.Inject

import com.github.t3hnar.bcrypt._
import commons.enums.{DatabaseError, FacebookError}
import controllers.SignUpController.{EmailSignUpDto, FacebookSighUpDto}
import controllers.base.{BaseController, FacebookCalls, RestResponses}
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, BodyParsers}
import security._
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}


class SignUpController @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                 tokenProvider: TokenProvider,
                                 tokenStorage: TokenStorage,
                                 ws: WSClient)
  extends BaseController(tokenStorage, dbConfigProvider) with RestResponses with FacebookCalls {

  override val wsClient: WSClient = ws

  def emailSignUp = Action.async(BodyParsers.parse.json) { request =>
    val parseResult = request.body.validate[EmailSignUpDto]
    parseResult match {
      case JsError(errors) => Future.successful(validationFailed(JsError.toJson(errors)))

      case JsSuccess(dto, p) =>
        val existsQuery = for {
          user <- Users if user.email === dto.email
        } yield user
        db.run(existsQuery.length.result).flatMap {
          case x if x == 0 =>
            //TODO: add Twilio number check
            val user = emailSighUpUser(dto)
            val insertQuery = Users.map(u => (u.firstName, u.lastName, u.email, u.phone, u.salt,
              u.password, u.userType)) returning Users.map(_.id)

            db.run((insertQuery += user).asTry).map {
              case Success(insertResult) =>
                val token = getToken(insertResult, (user._1, user._2, user._3, 0))
                ok(AuthResponse(token.key, token.userInfo.firstName, token.userInfo.lastName,
                  0, token.userInfo.verified))(AuthToken.authResponseFormat)

              case Failure(e) => badRequest(e.getMessage, DatabaseError)
            }

          case _ => Future.successful(validationFailed("User with this email already exists"))
        }
    }
  }

  def facebookSignUp = Action.async(BodyParsers.parse.json) { request =>
    val parseResult = request.body.validate[FacebookSighUpDto]
    parseResult match {
      case JsError(errors) => Future.successful(validationFailed(JsError.toJson(errors)))

      case JsSuccess(dto, jsPath) =>
        facebookMe(dto.token).flatMap { wsResponse =>
          wsResponse.status match {
            case 200 =>
              wsResponse.json.validate[FacebookResponseDto] match {
                case JsError(e) => Future.successful(badRequest("Invalid token", FacebookError))

                case JsSuccess(facebookDto, p) =>
                  val existsQuery = for {
                    u <- Users if u.facebookId.isDefined && u.facebookId === facebookDto.id
                  } yield u

                  db.run(existsQuery.length.result).flatMap {
                    case x if x == 0 =>
                      //TODO twilio call
                      val insertQuery = (Users.map(u => (u.firstName, u.lastName, u.email, u.phone, u.facebookId,
                        u.userType)) returning Users.map(_.id)) +=(dto.firstName, dto.lastName, facebookDto.email,
                        dto.phoneNumber, Some(facebookDto.id), 1)

                      db.run(insertQuery.asTry).map {
                        case Success(insertResult) =>
                          val token = getToken(insertResult, (dto.firstName, dto.lastName, facebookDto.email, 1))
                          ok(AuthResponse(token.key, token.userInfo.firstName, token.userInfo.lastName,
                            token.userInfo.userType, token.userInfo.verified))(AuthToken.authResponseFormat)

                        case Failure(e) => badRequest(e.getMessage, DatabaseError)
                      }

                    case _ => Future.successful(validationFailed("User with this facebook id already exists"))
                  }
              }

            case _ => Future.successful(badRequest(s"Failed to fetch data. Code - ${wsResponse.status}", FacebookError))
          }
        }
    }
  }

  private def getToken(id: Integer,
                       user: (String, String, Option[String], Int)) = {
    val userInfo = UserInfo(id, user._3, user._1, user._2, verified = false, user._4)
    val token = tokenProvider.generateToken(userInfo)
    tokenStorage.setToken(token)
  }

  private def emailSighUpUser(dto: EmailSignUpDto) = {
    val salt = generateSalt
    val hashedPassword = dto.password.bcrypt(salt)
    (dto.firstName, dto.lastName, Option(dto.email), dto.phoneNumber, Option(salt)
      , Option(hashedPassword), 0)
  }
}

object SignUpController {


  case class EmailSignUpDto(firstName: String,
                            lastName: String,
                            phoneNumber: String,
                            email: String,
                            password: String)

  case class FacebookSighUpDto(firstName: String,
                               lastName: String,
                               phoneNumber: String,
                               token: String)

  implicit val emailSignUpDtoReads: Reads[EmailSignUpDto] = (
      (JsPath \ "firstName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "lastName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "phoneNumber").read[String](pattern("\\+[0-9]{5,15}".r, "Invalid phone format")) and
      (JsPath \ "email").read[String](email) and
      (JsPath \ "password").read[String](minLength[String](6) keepAnd maxLength[String](32))
    )(EmailSignUpDto.apply _)

  implicit val facebookSighUpDtoReads: Reads[FacebookSighUpDto] = (
      (JsPath \ "firstName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "lastName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "phoneNumber").read[String](pattern("\\+[0-9]{5,15}".r, "Invalid phone format")) and
      (JsPath \ "token").read[String](minLength[String](10))
    )(FacebookSighUpDto.apply _)

}
