package actors.schedulers

import javax.inject.{Inject, Named}

import actors.TaskTimeSlotMigrationActor.MigrateTasks
import akka.actor.{ActorRef, ActorSystem, Cancellable}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.duration._

class TaskTimeSlotMigrationScheduler @Inject()(system: ActorSystem,
                                               @Named("taskTimeSlotMigrationActor") taskTimeSlotMigrationActor: ActorRef) {

  schedule()

  def schedule(): Cancellable = {
    Logger.info(s"Scheduling 'TaskTimeSlotMigrationActor'")
    system.scheduler.scheduleOnce(0.seconds, taskTimeSlotMigrationActor, MigrateTasks(0, 100))
  }

}
