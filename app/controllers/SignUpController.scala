package controllers

import javax.inject.Inject

import com.github.t3hnar.bcrypt._
import commons.enums.DatabaseError
import controllers.SignUpController.SignUpDto
import controllers.base.RestResponses
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.Crypto
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.mvc.{Action, BodyParsers, Controller}
import security.{AuthToken, UserInfo, TokenProvider, TokenStorage}
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class SignUpController @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                 tokenProvider: TokenProvider,
                                 tokenStorage: TokenStorage,
                                 crypto: Crypto) extends Controller with RestResponses {

  val db = dbConfigProvider.get[JdbcProfile].db

  def emailSighUp = Action.async(BodyParsers.parse.json) { request =>
    val parseResult = request.body.validate[SignUpDto]
    parseResult match {
      case JsError(errors) => Future.successful(validationFailed(JsError.toJson(errors)))

      case JsSuccess(dto, p) =>
        val existsQuery = for {
          user <- Users if user.email === dto.email
        } yield user
        db.run(existsQuery.length.result) flatMap  {
          case x if x == 0 =>
            //TODO: add Twillio number check
            val user= emailSighUpUser(dto)
            val userInsert = DBIO.seq(
              Users.map(u => (u.firstName, u.lastName, u.email, u.phone, u.salt, u.password, u.userType)) += user
            )
            db.run(userInsert) map { u =>
              val token = createToken(user)
              ok(token.key)
            }

          case _ => Future.successful(validationFailed("User with this email already exists"))
        }
    }
  }

  private def createToken(user: (String, String, Option[String], String, Option[String], Option[String], Int)) = {
    val userInfo = UserInfo(1, user._3, user._1, user._2, false, user._7)
    val token = tokenProvider.generateToken(userInfo)
    tokenStorage.setToken(token)
    token
  }

  private def emailSighUpUser(dto: SignUpDto) = {
    val salt = generateSalt
    val hashedPassword = dto.password.bcrypt(salt)
    (dto.firstName, dto.lastName, Option(dto.email), dto.phoneNumber, Option(salt)
      , Option(hashedPassword), 0)
  }
}

object SignUpController {

  case class SignUpDto(firstName: String,
                       lastName: String,
                       phoneNumber: String,
                       email: String,
                       password: String)

  implicit val emailDtoReads: Reads[SignUpDto] = (
      (JsPath \ "firstName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "lastName").read[String](minLength[String](2) keepAnd maxLength[String](150)) and
      (JsPath \ "phoneNumber").read[String](pattern("\\+[0-9]{5,15}".r, "Invalid phone format")) and
      (JsPath \ "email").read[String](email) and
      (JsPath \ "password").read[String](minLength[String](6) keepAnd maxLength[String](32))
    )(SignUpDto.apply _)
}
