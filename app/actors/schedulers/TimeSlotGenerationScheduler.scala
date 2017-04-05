package actors.schedulers

import javax.inject.{Inject, Named}

import actors.TimeSlotGenerationActor.GenerateTimeSlots
import akka.actor.{ActorRef, ActorSystem, Cancellable}
import play.api.Logger

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._


class TimeSlotGenerationScheduler @Inject()(system: ActorSystem,
                                            @Named("timeSlotGenerationActor") timeSlotGenerationActor: ActorRef) {

  schedule()

  def schedule(): Cancellable = {
    Logger.info(s"Scheduling 'TimeSlotGenerationActor' with ${7.days} interval")
    system.scheduler.schedule(1.minute, 7.days, timeSlotGenerationActor, GenerateTimeSlots)
  }
}
