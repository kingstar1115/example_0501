package services.internal.notifications

import java.util.concurrent.{TimeUnit, Future => JFuture}
import javax.inject.Inject

import services.internal.notifications.INotificationService._

import com.relayrides.pushy.apns.ApnsClient
import com.relayrides.pushy.apns.util.{ApnsPayloadBuilder, SimpleApnsPushNotification, TokenUtil}
import play.api.inject.ApplicationLifecycle
import play.api.{Environment, Logger}
import services.internal.cache.CacheService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Promise, Future => SFuture}
import scala.util.Try


class INotificationService @Inject()(lifecycle: ApplicationLifecycle,
                                     environment: Environment,
                                     cacheService: CacheService) extends PushNotificationService {

  var client = new ApnsClient[SimpleApnsPushNotification](environment.resourceAsStream("qweex_push.p12").get, "")
  val connectFuture = toScalaFuture(client.connect(ApnsClient.DEVELOPMENT_APNS_HOST))

  lifecycle.addStopHook { () =>
    Logger.debug("Stopping apns service")
    SFuture(client.disconnect())
  }

  override def sendJobAcceptedNotification(data: JobData, token: String): Unit = {
    sendNotification(token, buildJobAcceptedPayload(data))
  }

  override def sendJobStartedNotification(data: JobData, token: String): Unit = {
    sendNotification(token, buildJobStartedNotification(data))
  }

  override def sendJobInProgressNotification(data: JobData, token: String): Unit = {
    sendNotification(token, buildJobInProgressNotification(data))
  }

  override def sendJobCompleteNotification(data: JobData, tokenString: String) = {
    sendNotification(tokenString, buildJobCompletedPayload(data))
  }

  private def sendNotification(tokenString: String, buildPayload: => String) = {
    val payload = buildPayload
    val token = TokenUtil.sanitizeTokenString(tokenString)
    val notification = new SimpleApnsPushNotification(token, "Qweex", payload)
    toScalaFuture(client.sendNotification(notification))
  }

  private def buildJobAcceptedPayload(data: JobData) = {
    Logger.debug(s"Building job accepted notification: ${data.toString}")
    new ApnsPayloadBuilder()
      .addCustomProperty(JobId, data.jobId)
      .addCustomProperty(JobStatus, data.jobStatus)
      .setAlertBody(s"Car Wash was Accepted by ${data.agentName}.")
      .setAlertTitle("Car wash accepted")
      .buildWithDefaultMaximumLength()
  }

  private def buildJobStartedNotification(data: JobData) = {
    Logger.debug(s"Building job started notification: ${data.toString}")
    new ApnsPayloadBuilder()
      .addCustomProperty(JobId, data.jobId)
      .addCustomProperty(JobStatus, data.jobStatus)
      .setAlertBody(s"${data.agentName} from Qweex is on the way to your car.")
      .setAlertTitle("Agent on the way to your car")
      .buildWithDefaultMaximumLength()
  }

  private def buildJobInProgressNotification(data: JobData) = {
    Logger.debug(s"Building job started notification: ${data.toString}")
    new ApnsPayloadBuilder()
      .addCustomProperty(JobId, data.jobId)
      .addCustomProperty(JobStatus, data.jobStatus)
      .setAlertBody(s"Your car is being washed right now.")
      .setAlertTitle("Car washing in progress")
      .buildWithDefaultMaximumLength()
  }

  private def buildJobCompletedPayload(data: JobData) = {
    Logger.debug(s"Building job complete notification: ${data.toString}")
    new ApnsPayloadBuilder()
      .addCustomProperty(JobId, data.jobId)
      .addCustomProperty(JobStatus, data.jobStatus)
      .setAlertBody(s"You car is now clean. Don't forget to rate and tip ${data.agentName}")
      .setAlertTitle("Car wash completed")
      .buildWithDefaultMaximumLength()
  }

  private def toScalaFuture[T](jFuture: => JFuture[T]) = {
    val promise = Promise[T]()
    new Thread(new Runnable {
      override def run(): Unit = {
        promise.complete(Try(jFuture.get(15, TimeUnit.SECONDS)))
      }
    })
    promise.future
  }

  override def getCacheService = cacheService
}

object INotificationService {
  val JobId = "jobId"
  val JobStatus = "jobStatus"
}