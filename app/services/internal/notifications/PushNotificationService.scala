package services.internal.notifications

import services.internal.cache.CacheService


trait PushNotificationService {

  def getCacheService: CacheService

  def sendJobStartedNotification(data: JobNotificationData, token: String): Unit

  def sendJobInProgressNotification(data: JobNotificationData, token: String): Unit

  def sendJobCompleteNotification(data: JobNotificationData, token: String): Unit

  def getUserDeviceTokens(userId: Int): List[String] = {
    getCacheService.get[String](userId.toString)
      .map(_.split(" ").toList)
      .getOrElse(List.empty)
  }

  def setUserDeviceTokens(userId: Int, tokens: List[String]) = {
    getCacheService.set(userId.toString, tokens.mkString(" ").trim)
  }

  def subscribeDevice(userId: Int, deviceToken: String): Boolean = {
    val tokens = getUserDeviceTokens(userId)
    tokens.contains(deviceToken) match {
      case false =>
        setUserDeviceTokens(userId, tokens.::(deviceToken))
      case _ =>
        true
    }
  }

  def unsubscribeDevice(userId: Int, deviceToken: String): Boolean = {
    val tokens = getUserDeviceTokens(userId)
    tokens match {
      case list if list.contains(deviceToken) =>
        setUserDeviceTokens(userId, tokens.filterNot(_.equals(deviceToken)))
      case _ =>
        false
    }
  }
}

case class JobNotificationData(jobId: Long,
                               agentName: String,
                               jobStatus: Int,
                               userId: Int)

