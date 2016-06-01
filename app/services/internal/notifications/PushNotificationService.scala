package services.internal.notifications

import services.internal.cache.CacheService


trait PushNotificationService {

  def getCacheService: CacheService

  def sendJobAcceptedNotification(data: JobData, token: String): Unit

  def sendJobStartedNotification(data: JobData, token: String): Unit

  def sendJobInProgressNotification(data: JobData, token: String): Unit

  def sendJobCompleteNotification(data: JobData, token: String): Unit

  def subscribeDevice(userId: Int, deviceToken: String): Boolean = {
    val tokens = getCacheService.getUserDeviceTokens(userId)
    tokens.contains(deviceToken) match {
      case false =>
        getCacheService.setUserDeviceTokens(userId, tokens.::(deviceToken))
      case _ =>
        true
    }

  }

  def unsubscribeDevice(userId: Int, deviceToken: String): Boolean = {
    val tokens = getCacheService.getUserDeviceTokens(userId)
    tokens match {
      case list if list.contains(deviceToken) =>
        getCacheService.setUserDeviceTokens(userId, tokens.filterNot(_.equals(deviceToken)))
      case _ =>
        false
    }
  }
}

case class JobData(jobId: Long,
                   agentName: String,
                   jobStatus: Int)

