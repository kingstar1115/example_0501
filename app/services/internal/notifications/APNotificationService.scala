package services.internal.notifications

import java.util.concurrent.{TimeUnit, Future => JFuture}
import javax.inject.Inject

import com.relayrides.pushy.apns.ApnsClient
import com.relayrides.pushy.apns.util.{ApnsPayloadBuilder, SimpleApnsPushNotification, TokenUtil}
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Environment, Logger}
import services.internal.cache.CacheService
import services.internal.notifications.APNotificationService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.util.Try


class APNotificationService @Inject()(lifecycle: ApplicationLifecycle,
                                      environment: Environment,
                                      cacheService: CacheService,
                                      config: Configuration) extends PushNotificationService {

  val devMode = config.getBoolean("apns.dev.mode").get
  val p12FileName = if (devMode) "qweex_push.p12" else "qweex_push_test.p12"
  val serverAddress = if (devMode) ApnsClient.DEVELOPMENT_APNS_HOST else ApnsClient.PRODUCTION_APNS_HOST

  var client = new ApnsClient[SimpleApnsPushNotification](environment.resourceAsStream(p12FileName).get, "")
  Logger.info("Connecting to APNs")
  val connectFuture = toScalaFuture(client.connect(serverAddress))
    .map(_ => Logger.info("Connected to APNs"))

  lifecycle.addStopHook { () =>
    Logger.info("Stopping APNs service")
    toScalaFuture(client.disconnect())
      .map(_ => Logger.info("APNs service is stopped"))
  }

  override def sendJobAcceptedNotification(data: JobNotificationData, token: String): Unit = {
    sendNotification(token, data) { data =>
      Logger.debug(s"Building job accepted notification: ${data.toString}")
      new ApnsPayloadBuilder()
        .addCustomProperty(JobId, data.jobId)
        .addCustomProperty(JobStatus, data.jobStatus)
        .setAlertBody(s"Car Wash was Accepted by ${data.agentName}.")
        .setAlertTitle("Car wash accepted")
        .buildWithDefaultMaximumLength()
    }
  }

  override def sendJobStartedNotification(data: JobNotificationData, token: String): Unit = {
    sendNotification(token, data) { data =>
      Logger.debug(s"Building job started notification: ${data.toString}")
      new ApnsPayloadBuilder()
        .addCustomProperty(JobId, data.jobId)
        .addCustomProperty(JobStatus, data.jobStatus)
        .setAlertBody(s"${data.agentName} from Qweex is on the way to your car.")
        .setAlertTitle("Agent on the way to your car")
        .buildWithDefaultMaximumLength()
    }
  }

  override def sendJobInProgressNotification(data: JobNotificationData, token: String): Unit = {
    sendNotification(token, data) { data =>
      Logger.debug(s"Building job started notification: ${data.toString}")
      new ApnsPayloadBuilder()
        .addCustomProperty(JobId, data.jobId)
        .addCustomProperty(JobStatus, data.jobStatus)
        .setAlertBody(s"Your car is being washed right now.")
        .setAlertTitle("Car washing in progress")
        .buildWithDefaultMaximumLength()
    }
  }

  override def sendJobCompleteNotification(data: JobNotificationData, tokenString: String) = {
    sendNotification(tokenString, data) { data =>
      Logger.debug(s"Building job complete notification: ${data.toString}")
      new ApnsPayloadBuilder()
        .addCustomProperty(JobId, data.jobId)
        .addCustomProperty(JobStatus, data.jobStatus)
        .setAlertBody(s"You car is now clean. Don't forget to rate and tip ${data.agentName}.")
        .setAlertTitle("Car wash completed")
        .buildWithDefaultMaximumLength()
    }
  }

  private def sendNotification(deviceToken: String, notificationData: JobNotificationData)(buildPayload: JobNotificationData => String) = {
    val payload = buildPayload(notificationData)
    val token = TokenUtil.sanitizeTokenString(deviceToken)
    val notification = new SimpleApnsPushNotification(token, "co.qweex.qweexapp", payload)
    toScalaFuture(client.sendNotification(notification)).map { pushNotificationResponse =>
      pushNotificationResponse.isAccepted match {
        case true =>
          Logger.debug("Push notification accepted by APNs gateway")
        case _ =>
          Logger.debug(s"Notification rejected by the APNs gateway: ${pushNotificationResponse.getRejectionReason}")
          if (pushNotificationResponse.getTokenInvalidationTimestamp != null) {
            Logger.debug(s"Invalid token: $deviceToken for user ${notificationData.userId}")
            unsubscribeDevice(notificationData.userId, deviceToken)
          }
      }
    }
  }

  private def toScalaFuture[T](jFuture: => JFuture[T]) = {
    val promise = Promise[T]()
    new Thread(new Runnable {
      override def run(): Unit = {
        promise.complete(Try(jFuture.get(15, TimeUnit.SECONDS)))
      }
    }).start()
    promise.future
  }

  override def getCacheService = cacheService
}

object APNotificationService {
  val JobId = "jobId"
  val JobStatus = "jobStatus"
}
