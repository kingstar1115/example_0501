package actors.schedulers

import javax.inject.{Inject, Named}

import actors.EdmundsMigrationActor.MigrateEdmundsData
import akka.actor.{ActorRef, ActorSystem, Cancellable}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.duration._


class EdmundsMigrationScheduler @Inject()(system: ActorSystem,
                                          @Named("edmundsMigrationActor") edmundsMigrationActor: ActorRef) {

  schedule()

  def schedule(): Cancellable = {
    Logger.info(s"Scheduling 'EdmundsMigrationActor' with ${1.days} interval")
    system.scheduler.scheduleOnce(15.second, edmundsMigrationActor, MigrateEdmundsData)
  }
}
