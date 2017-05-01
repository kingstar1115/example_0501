package actors.schedulers

import javax.inject.{Inject, Named}

import actors.TimeSlotGenerationActor.GenerateTimeSlots
import akka.actor.{ActorRef, ActorSystem, Cancellable}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.duration._


class TimeSlotGenerationScheduler @Inject()(system: ActorSystem,
                                            @Named("timeSlotGenerationActor") timeSlotGenerationActor: ActorRef) {

  schedule()

  def schedule(): Cancellable = {
    Logger.info(s"Scheduling 'TimeSlotGenerationActor' with ${1.days} interval")
    system.scheduler.schedule(5.minute, 1.days, timeSlotGenerationActor, GenerateTimeSlots)
  }
}
