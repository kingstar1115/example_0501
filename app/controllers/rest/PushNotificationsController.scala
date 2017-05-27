package controllers.rest

import javax.inject.Inject

import controllers.rest.base._
import controllers.rest.PushNotificationsController._
import play.api.libs.json._
import security.TokenStorage
import services.internal.notifications.PushNotificationService

//noinspection TypeAnnotation
class PushNotificationsController @Inject()(val tokenStorage: TokenStorage,
                                            pushNotificationService: PushNotificationService) extends BaseController {

  implicit val deviceTokenReads = (__ \ 'token).read[String].map(DeviceToken.apply)

  def subscribe(version: String) = authorized(parse.json) { request =>
    processRequest[DeviceToken](request.body) { dto =>
      pushNotificationService.subscribeDevice(request.token.get.userInfo.id, dto.token) match {
        case true => success
        case _ => badRequest("Failed to subscribe device token")
      }
    }
  }

  def unsubscribe(version: String) = authorized(parse.json) { request =>
    processRequest[DeviceToken](request.body) { dto =>
      pushNotificationService.unsubscribeDevice(request.token.get.userInfo.id, dto.token) match {
        case true => success
        case _ => badRequest("Failed to unsubscribe device token")
      }
    }
  }
}

object PushNotificationsController {

  case class DeviceToken(token: String)

}
