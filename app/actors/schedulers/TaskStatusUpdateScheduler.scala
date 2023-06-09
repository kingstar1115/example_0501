package actors.schedulers

import actors.TaskStatusUpdateActor.UpdateOverdueTasks
import akka.actor.{ActorRef, ActorSystem, Cancellable}
import javax.inject.{Inject, Named}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.duration._

class TaskStatusUpdateScheduler @Inject()(system: ActorSystem,
                                          @Named("taskStatusUpdateActor") taskStatusUpdateActor: ActorRef) {

  private val logger = Logger(this.getClass)

  schedule()

  def schedule(): Cancellable = {
    logger.info(s"Scheduling 'TaskStatusUpdateActor' with interval ${1.hour}")
    system.scheduler.schedule(1.minute, 1.hour, taskStatusUpdateActor, UpdateOverdueTasks)
  }

}
