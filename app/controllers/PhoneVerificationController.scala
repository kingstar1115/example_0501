package controllers

import com.google.inject.Inject
import commons.enums.{AuthyError, DatabaseError}
import controllers.base.BaseController
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import security.AuthyVerifyService.CheckResponseDto
import security.{AuthyVerifyService, TokenStorage}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class PhoneVerificationController @Inject()(dbConfigProvider: DatabaseConfigProvider,
                                            tokenStorage: TokenStorage,
                                            verifyService: AuthyVerifyService) extends BaseController(tokenStorage, dbConfigProvider) {

  def verifyCode(code: String) = authenticatedAction.async { request =>
    val userId = request.token.get.userInfo.id
    val userQuery = for {u <- Users if u.id === userId && u.verified === false} yield u.phone
    db.run(userQuery.take(1).result).flatMap { resultSet =>
      resultSet.headOption.map { phone =>
        verifyService.checkVerifyCode(phone.charAt(1).toInt, phone.substring(2), code).flatMap {
          case success: CheckResponseDto if success.success =>
            val updateQuery = for (u <- Users if u.id === userId) yield u.verified
            db.run(updateQuery.update(true)).map {
              case 1 => ok("Success")
              case _ => badRequest("Can't verify user", DatabaseError)
            }

          case failed => Future.successful(badRequest(failed.message, AuthyError))
        }
      } getOrElse Future.successful(NotFound)
    }
  }
}
