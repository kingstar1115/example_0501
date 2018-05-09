package services

import java.time.LocalDateTime

import javax.inject.Inject
import com.google.inject.Singleton
import models.Tables.TasksRow
import play.api.Configuration
import play.api.i18n.{I18nSupport, Messages, MessagesApi}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.RequestHeader

@Singleton
class EmailService @Inject()(mailerClient: MailerClient,
                             config: Configuration,
                             val messagesApi: MessagesApi) extends I18nSupport {

  private val noReplyEmail = config.getString("play.mailer.user").get

  def sendPasswordForgetEmail(email: String, passwordRecoverUrl: String)(implicit requestHeader: RequestHeader): String = {
    val messageBody = Messages("email.forget.body", passwordRecoverUrl)
    val mail = Email(Messages("email.forget.title"),
      noReplyEmail,
      Seq(email),
      bodyHtml = Option(messageBody))
    mailerClient.send(mail)
  }

  def sendUserRegisteredEmail(firstName: String, lastName: String, email: String)(implicit requestHeader: RequestHeader): String = {
    val messageBody = Messages("email.registration.body", firstName, lastName, email)
    val mail = Email(Messages("email.registration.title"),
      noReplyEmail,
      Seq("newuser@qweex.co"),
      bodyHtml = Option(messageBody))
    mailerClient.send(mail)
  }

  def sendOverdueTasksNotification(dateTime: LocalDateTime, tasks: Seq[TasksRow]): Unit = {
    val messageBody = Messages("email.tasks.overdue.body", dateTime, tasks.map(_.jobId).mkString(", "))
    val mail = Email(Messages("email.tasks.overdue.title"),
      noReplyEmail,
      Seq("dispatch@qweex.co", "valera.rusakov@gmail.com"),
      bodyHtml = Option(messageBody)
    )
    mailerClient.send(mail)
  }
}
