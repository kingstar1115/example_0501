package services.internal.notifications

import java.util.concurrent.{TimeUnit, Future => JFuture}

import com.relayrides.pushy.apns.util.{ApnsPayloadBuilder, SimpleApnsPushNotification, TokenUtil}
import com.relayrides.pushy.apns.{ApnsClient, ApnsClientBuilder}
import javax.inject.Inject
import play.api.inject.ApplicationLifecycle
import play.api.libs.concurrent.Execution.Implicits._
import play.api.{Configuration, Environment, Logger}
import services.internal.cache.CacheService
import services.internal.notifications.APNotificationService._

import scala.concurrent.Promise
import scala.util.Try


class APNotificationService @Inject()(lifecycle: ApplicationLifecycle,
                                      environment: Environment,
                                      cacheService: CacheService,
                                      config: Configuration) extends PushNotificationService {

  private val logger = Logger(this.getClass)

  private val p12FileName = config.getString("apns.certificate").get
  private val password = config.getString("apns.password").getOrElse("")
  private val topic = config.getString("apns.topic").get
  private val serverAddress = if (config.getBoolean("apns.production").get)
    ApnsClient.PRODUCTION_APNS_HOST
  else
    ApnsClient.DEVELOPMENT_APNS_HOST

  private val client = new ApnsClientBuilder()
    .setClientCredentials(environment.resourceAsStream(p12FileName).get, password)
    .build()
  logger.info(s"Connecting to APNs. Certificate $p12FileName. Topic $topic")
  toScalaFuture(client.connect(serverAddress))
    .map(_ => logger.info("Connected to APNs"))

  lifecycle.addStopHook { () =>
    logger.info("Stopping APNs service")
    toScalaFuture(client.disconnect())
      .map(_ => logger.info("APNs service is stopped"))
  }

  override def sendJobStartedNotification(data: JobNotificationData, token: String): Unit = {
    sendNotification(token, data) { data =>
      logger.info(s"Building job started notification: ${data.toString}")
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
      logger.info(s"Building job in progress notification: ${data.toString}")
      new ApnsPayloadBuilder()
        .addCustomProperty(JobId, data.jobId)
        .addCustomProperty(JobStatus, data.jobStatus)
        .setAlertBody(s"Your car is being washed right now.")
        .setAlertTitle("Car washing in progress")
        .buildWithDefaultMaximumLength()
    }
  }

  override def sendJobCompleteNotification(data: JobNotificationData, tokenString: String): Unit = {
    sendNotification(tokenString, data) { data =>
      logger.info(s"Building job complete notification: ${data.toString}")
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
    val notification = new SimpleApnsPushNotification(token, topic, payload)
    toScalaFuture(client.sendNotification(notification)).map { pushNotificationResponse =>
      if (pushNotificationResponse.isAccepted) {
        logger.info(s"Push notification `$notification` accepted by APNs gateway")
      } else {
        logger.info(s"Push notification `$notification` rejected by the APNs gateway: ${pushNotificationResponse.getRejectionReason}")
        if (pushNotificationResponse.getTokenInvalidationTimestamp != null) {
          logger.info(s"Invalid token: $deviceToken for user ${notificationData.userId}")
          unsubscribeDevice(notificationData.userId, deviceToken)
        }
      }
    }
  }

  //noinspection ConvertExpressionToSAM
  private def toScalaFuture[T](jFuture: => JFuture[T]) = {
    val promise = Promise[T]()
    new Thread(new Runnable {
      override def run(): Unit = {
        promise.complete(Try(jFuture.get(15, TimeUnit.SECONDS)))
      }
    }).start()
    promise.future
  }

  override def getCacheService: CacheService = cacheService
}

object APNotificationService {
  val JobId = "jobId"
  val JobStatus = "jobStatus"
}
