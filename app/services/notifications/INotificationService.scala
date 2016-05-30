package services.notifications

import java.io.File
import java.util.concurrent.{TimeUnit, Future => JFuture}
import javax.inject.Inject

import com.relayrides.pushy.apns.ApnsClient
import com.relayrides.pushy.apns.util.{ApnsPayloadBuilder, SimpleApnsPushNotification, TokenUtil}
import play.api.inject.ApplicationLifecycle
import play.api.{Environment, Logger}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Promise, Future => SFuture}
import scala.util.Try


class INotificationService @Inject()(lifecycle: ApplicationLifecycle,
                                     environment: Environment) extends PushNotificationService {

  var client = new ApnsClient[SimpleApnsPushNotification](new File(new File(environment.rootPath, "cert"), "qweex_push.p12"), "")
  val connectFuture = toScalaFuture(client.connect(ApnsClient.DEVELOPMENT_APNS_HOST))

  lifecycle.addStopHook { () =>
    Logger.debug("Stopping apns service")
    SFuture(client.disconnect())
  }

  override def sendJobCompleteNotification(data: JobData, tokenString: String) = {
    connectFuture.flatMap { _ =>
      val token = TokenUtil.sanitizeTokenString(tokenString)
      val payload = buildJobCompletedPayload(data)
      val notification = new SimpleApnsPushNotification(token, "Qweex", payload)
      toScalaFuture(client.sendNotification(notification))
    }
  }

  private def buildJobCompletedPayload(data: JobData) = {
    new ApnsPayloadBuilder()
      .addCustomProperty("jobId", data.jobId)
      .setAlertBody(s"${data.agentName} has completed your car wash, don't forget to rate and tip")
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

}
