package services

import java.io.{FileInputStream, File}
import javax.inject.{Inject, Singleton}

import com.relayrides.pushy.apns.{PushManagerConfiguration, ApnsEnvironment, PushManager}
import com.relayrides.pushy.apns.util.{ApnsPayloadBuilder, TokenUtil, SSLContextUtil, SimpleApnsPushNotification}
import play.api.{Configuration, Logger}
import play.api.inject.ApplicationLifecycle
import services.ApnsPushService._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


@Singleton
class ApnsPushService @Inject()(lifecycle: ApplicationLifecycle,
                                fileService: FileService,
                                configuration: Configuration) {

  private val CERTIFICATE = configuration.getString("notifications.ios.certificate").get
  private val CERT_PASSWORD = configuration.getString("notifications.ios.password").get

  private val manager = new PushManager[SimpleApnsPushNotification](
    ApnsEnvironment.getSandboxEnvironment,
    SSLContextUtil.createDefaultSSLContext(getCertificate, CERT_PASSWORD),
    null,
    null,
    null,
    new PushManagerConfiguration(),
    "ApnsManager")
  manager.start()

  lifecycle.addStopHook(() => Future.successful {
    Try(manager.shutdown()) match {
      case Success(r) => Logger.info("ApnsManager stopped sucesfully")
      case Failure(e) => Logger.info("Failed to stope ApnsManager", e)
    }
  })

  def sendNotification(token: String, notification: Notification) = {
    val byteArray: Array[Byte] = TokenUtil.tokenStringToByteArray(token)

    val builder = new ApnsPayloadBuilder
    builder.setAlertTitle(notification.title)
    builder.setAlertBody(notification.message)

    manager.getQueue.put(new SimpleApnsPushNotification(byteArray, builder.buildWithDefaultMaximumLength()))
  }

  private def getCertificate = {
    val cert = new File(fileService.getFolder(FileService.CERT_FOLDER), CERTIFICATE)
    new FileInputStream(cert)
  }
}

object ApnsPushService {

  case class Notification(title: String,
                          message: String)

}
