package controllers

import javax.inject.Inject

import com.github.t3hnar.bcrypt._
import commons.enums.DatabaseError
import controllers.SignUpController.EmailSignUpDto
import controllers.base.RestResponses
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.{Action, BodyParsers, Controller}
import security._
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success}


class SignUpController @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                 tokenProvider: TokenProvider,
                                 tokenStorage: TokenStorage) extends Controller with RestResponses {

  val db = dbConfigProvider.get[JdbcProfile].db

  def emailSighUp = Action.async(BodyParsers.parse.json) { request =>
    val parseResult = request.body.validate[EmailSignUpDto]
    parseResult match {
      case JsError(errors) => Future.successful(validationFailed(JsError.toJson(errors)))

      case JsSuccess(dto, p) =>
        val existsQuery = for {
          user <- Users if user.email === dto.email
        } yield user
        db.run(existsQuery.length.result) flatMap {
          case x if x == 0 =>
            //TODO: add Twillio number check
            val user = emailSighUpUser(dto)
            val insertQuery = Users.map(u => (u.firstName, u.lastName, u.email, u.phone, u.salt,
              u.password, u.userType)) returning Users.map(_.id)
            db.run((insertQuery += user).asTry) map {
              case Success(insertResult) =>
                val token = getToken(insertResult, user)
                ok(AuthResponse(token.key, token.userInfo.name, token.userInfo.surname,
                  0, token.userInfo.verified))(AuthToken.authResponseFormat)

              case Failure(e) => badRequest(e.getMessage, DatabaseError)
            }

          case _ => Future.successful(validationFailed("User with this email already exists"))
        }
    }
  }

  private def getToken(id: Integer,
                          user: (String, String, Option[String], String, Option[String], Option[String], Int)) = {
    val userInfo = UserInfo(id, user._3, user._1, user._2, verified = false, user._7)
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

  implicit val emailSignUpDtoReads: Reads[EmailSignUpDto] = (
      (JsPath \ "firstName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "lastName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "phoneNumber").read[String](pattern("\\+[0-9]{5,15}".r, "Invalid phone format")) and
      (JsPath \ "email").read[String](email) and
      (JsPath \ "password").read[String](minLength[String](6) keepAnd maxLength[String](32))
    )(EmailSignUpDto.apply _)
}
