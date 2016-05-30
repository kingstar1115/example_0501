package services.notifications


trait PushNotificationService {

  def sendJobCompleteNotification(data: JobData, token: String): Unit

}

case class JobData(jobId: Long, agentName: String)

