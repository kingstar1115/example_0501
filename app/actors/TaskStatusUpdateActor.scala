package actors

import java.time.{ZoneId, ZonedDateTime}

import actors.TaskStatusUpdateActor.UpdateOverdueTasks
import akka.actor.Actor
import dao.tasks.TasksDao
import javax.inject.Inject
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import services.EmailService
import services.internal.tasks.TasksService

class TaskStatusUpdateActor @Inject()(tasksDao: TasksDao,
                                      tasksService: TasksService,
                                      mailService: EmailService) extends Actor {

  private val logger = Logger(this.getClass)

  override def receive: Receive = {
    case UpdateOverdueTasks =>
      val pstDateTime = ZonedDateTime.now(ZoneId.of("America/Los_Angeles")).toLocalDateTime
      tasksDao.getOverdueTasks(pstDateTime)
        .filter(_.nonEmpty)
        .map(overdueTasks => {
          logger.warn(s"Found ${overdueTasks.length} overdue tasks in progress for $pstDateTime")
          overdueTasks.foreach(overdueTask => tasksService.refreshTask(overdueTask.jobId))
          mailService.sendOverdueTasksNotification(pstDateTime, overdueTasks)
        })

    case _ =>
  }
}

object TaskStatusUpdateActor {

  case object UpdateOverdueTasks

}
