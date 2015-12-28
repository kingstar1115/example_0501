package controllers

import com.google.inject.Inject
import commons.enums.{AuthyError, DatabaseError}
import controllers.PhoneVerificationController._
import controllers.base.BaseController
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.{JsSuccess, JsError, Reads, JsPath}
import play.api.libs.json.Reads._
import play.api.libs.functional.syntax._
import play.api.mvc.BodyParsers
import security.AuthyVerifyService.AuthyResponseDto
import security.{AuthToken, AuthResponse, AuthyVerifyService, TokenStorage}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class PhoneVerificationController @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                            val tokenStorage: TokenStorage,
                                            verifyService: AuthyVerifyService) extends BaseController() {

  val db = dbConfigProvider.get.db

  def verifyCode(code: String) = authorized.async { request =>
    val token = request.token.get
    val user = token.userInfo
    val userQuery = for {u <- Users if u.id === user.id && u.verified === false} yield (u.phoneCode, u.phone)
    db.run(userQuery.result.headOption).flatMap { resultSet =>
      resultSet.map { phone =>
        verifyService.checkVerifyCode(phone._1, phone._2, code).flatMap {
          case success: AuthyResponseDto if success.success =>
            val updateQuery = for (u <- Users if u.id === user.id) yield u.verified
            db.run(updateQuery.update(true)).map {
              case 1 =>
                val updatedToken = token.copy(userInfo = user.copy(verified = true))
                tokenStorage.updateToken(updatedToken)
                ok(AuthResponse(updatedToken.key, updatedToken.userInfo.firstName, updatedToken.userInfo.lastName,
                  0, updatedToken.userInfo.verified, updatedToken.userInfo.picture))(AuthToken.authResponseFormat)
              case _ => badRequest("Can't verify user", DatabaseError)
            }

          case failed => Future.successful(badRequest(failed.message, AuthyError))
        }
      } getOrElse Future.successful(NotFound)
    }
  }

  def resendCode = authorized.async { request =>
    val phoneQuery = for {u <- Users if u.id === request.token.get.userInfo.id} yield (u.phoneCode, u.phone)
    db.run(phoneQuery.result) flatMap { r =>
      verifyService.sendVerifyCode(r.head._1.toInt, r.head._2) map {
        case verifyResponse if verifyResponse.success => ok("Verification code has been sent")
        case other => badRequest(other.message, AuthyError)
      } recover {
        case e: Exception => serverError(e)
      }
    }
  }

  def changePhoneNumber = authorized.async(BodyParsers.parse.json) { request =>
    request.body.validate[PhoneChangeDto] match {
      case JsError(errors) => Future.successful(validationFailed(JsError.toJson(errors)))

      case JsSuccess(dto, p) =>
        verifyService.sendVerifyCode(dto.phoneCountryCode.toInt, dto.phoneNumber) flatMap { verifyResponse =>
          verifyResponse.success match {
            case false => Future.successful(badRequest(verifyResponse.message, AuthyError))
            case true =>
              val updateQuery = for {
                u <- Users if u.id === request.token.get.userInfo.id
              } yield (u.phoneCode, u.phone, u.verified)
              db.run(updateQuery.update(dto.phoneCountryCode, dto.phoneNumber, false)) map { updateResult =>
                val token = request.token.get
                val user = token.userInfo
                val updatedToken = token.copy(userInfo = user.copy(verified = false))
                tokenStorage.updateToken(updatedToken)
                ok("Phone updated")
              }
          }
        } recover {
          case e: Exception => serverError(e)
        }
    }
  }
}

object PhoneVerificationController {

  case class PhoneChangeDto(phoneCountryCode: String, phoneNumber: String)

  implicit val phoneChangeDtoReads: Reads[PhoneChangeDto] = (
      (JsPath \ "phoneCountryCode").read[String](pattern("[0-9]{1,4}".r, "Invalid country code")) and
      (JsPath \ "phoneNumber").read[String](pattern("[0-9]{8,14}".r, "Invalid phone format"))
    )(PhoneChangeDto.apply _)
}
