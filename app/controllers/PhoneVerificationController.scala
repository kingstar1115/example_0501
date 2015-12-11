package controllers

import com.google.inject.Inject
import commons.enums.{AuthyError, DatabaseError}
import controllers.base.{SimpleResponse, BaseController}
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import security.AuthyVerifyService.CheckResponseDto
import security.{AuthToken, AuthResponse, AuthyVerifyService, TokenStorage}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class PhoneVerificationController @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                            val tokenStorage: TokenStorage,
                                            verifyService: AuthyVerifyService) extends BaseController() {

  val db = dbConfigProvider.get.db

  def verifyCode(code: String) = authenticatedAction.async { request =>
    val token = request.token.get
    val user = token.userInfo
    val userQuery = for {u <- Users if u.id === user.id && u.verified === false} yield (u.phoneCode, u.phone)
    db.run(userQuery.take(1).result).flatMap { resultSet =>
      resultSet.headOption.map { phone =>
        verifyService.checkVerifyCode(phone._1, phone._2, code).flatMap {
          case success: CheckResponseDto if success.success =>
            val updateQuery = for (u <- Users if u.id === user.id) yield u.verified
            db.run(updateQuery.update(true)).map {
              case 1 =>
                val updatedToken = token.copy(userInfo = user.copy(verified = true))
                tokenStorage.updateToken(updatedToken)
                ok(AuthResponse(updatedToken.key, updatedToken.userInfo.firstName, updatedToken.userInfo.lastName,
                  0, updatedToken.userInfo.verified))(AuthToken.authResponseFormat)
              case _ => badRequest("Can't verify user", DatabaseError)
            }

          case failed => Future.successful(badRequest(failed.message, AuthyError))
        }
      } getOrElse Future.successful(NotFound)
    }
  }

  def resendCode = authenticatedAction.async { request =>
    val phoneQuery = for {u <- Users if u.id === request.token.get.userInfo.id} yield (u.phoneCode, u.phone)
    db.run(phoneQuery.result) flatMap { r =>
      verifyService.sendVerifyCode(r.head._1.toInt, r.head._2) map {
        case verifyResponse if verifyResponse.success => ok(SimpleResponse("Success"))
        case other => badRequest(other.message, AuthyError)
      } recover {
        case e: Exception => serverError(e)
      }
    }
  }
}
