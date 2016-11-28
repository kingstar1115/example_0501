package services

import javax.inject.Inject

import com.google.inject.Singleton
import play.api.Configuration
import play.api.i18n.{MessagesApi, I18nSupport, Messages}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.mvc.RequestHeader

@Singleton
class EmailService @Inject()(mailerClient: MailerClient,
                             config: Configuration,
                             val messagesApi: MessagesApi) extends I18nSupport {

  val noReplyEmail = config.getString("play.mailer.user").get

  def sendPasswordForgetEmail(email: String, passwordRecoverUrl: String)(implicit requestHeader: RequestHeader) = {
    val messageBody = Messages("email.forget.body", passwordRecoverUrl)
    val mail = Email(Messages("email.forget.title"),
      noReplyEmail,
      Seq(email),
      bodyHtml = Option(messageBody))
    mailerClient.send(mail)
  }

  def sendUserRegisteredEmail(firstName: String, lastName: String, email: String)(implicit requestHeader: RequestHeader) = {
    val messageBody = Messages("email.registration.body", firstName, lastName, email)
    val mail = Email(Messages("email.registration.title"),
      noReplyEmail,
      Seq("yegormakarov@gmail.com"),
      bodyHtml = Option(messageBody))
    mailerClient.send(mail)
  }

}
