package controllers

import com.github.t3hnar.bcrypt._
import com.google.inject.Inject
import models.Tables
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.db.slick.DatabaseConfigProvider
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, Controller}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import views.html.password._


class PasswordRecoveryController @Inject()(dbConfigProvider: DatabaseConfigProvider, val messagesApi: MessagesApi)
  extends Controller with I18nSupport {

  def getRecoverPasswordPage(code: String) = Action.async { request =>
    val db = dbConfigProvider.get.db
    val userQuery = for {u <- Tables.Users if u.code === code} yield u
    db.run(userQuery.result.headOption).map { userOpt =>
      userOpt.map { user =>
        val form = Form(
          mapping(
            "code" -> text,
            "password" -> text,
            "passwordRepeat" -> text
          )(PasswordRecovery.apply)(PasswordRecovery.unapply))
        val data = form.fill(new PasswordRecovery(user.code.get, "", ""))
        Ok(passwordRestorePage(data))
      }.getOrElse(Redirect(routes.PasswordRecoveryController.notFound()))
    }
  }

  def submitForm() = Action.async { implicit request =>
    val passwordConstraint: Constraint[PasswordRecovery] = Constraint("constraints.passwordMatch")({
      data => if (data.password == data.passwordRepeat) Valid else Invalid(Seq(ValidationError("Passwords must match")))
    })
    val form = Form(
      mapping(
        "code" -> nonEmptyText(minLength = 6),
        "password" -> nonEmptyText(minLength = 6, maxLength = 32),
        "passwordRepeat" -> nonEmptyText(minLength = 6, maxLength = 32)
      )(PasswordRecovery.apply)(PasswordRecovery.unapply).verifying(passwordConstraint)
    )
    form.bindFromRequest.fold(
      formWithErrors => {
        Future.successful(BadRequest(passwordRestorePage(formWithErrors)))
      },
      successForm => {
        val db = dbConfigProvider.get.db
        val userQuery = for {u <- Tables.Users if u.code === successForm.code} yield u
        db.run(userQuery.result.headOption).flatMap { userOpt =>
          userOpt.map { user =>
            val userSalt = user.salt
            val newPassword = successForm.password.bcrypt(userSalt)
            val updateQuery = Tables.Users.map(user => (user.code, user.password))
              .filter(_._1 === user.code)
              .update(None, Some(newPassword))
            db.run(updateQuery).map(count => Redirect(routes.PasswordRecoveryController.successPage()))
          }.getOrElse(Future.successful(BadRequest))
        }
      }
    )
  }

  def successPage = Action { implicit request =>
    Ok(messagePage("Success", "Password changed successfully!"))
  }

  def notFound = Action { implicit request =>
    Ok(messagePage("404", "Not found"))
  }
}

case class PasswordRecovery(code: String,
                            password: String,
                            passwordRepeat: String)
