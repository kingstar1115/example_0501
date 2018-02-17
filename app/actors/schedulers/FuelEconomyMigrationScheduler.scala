package actors.schedulers

import javax.inject.{Inject, Named}

import actors.FuelEconomyMigrationActor.MigrateEdmundsData
import akka.actor.{ActorRef, ActorSystem, Cancellable}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.duration._


class FuelEconomyMigrationScheduler @Inject()(system: ActorSystem,
                                              @Named("fuelEconomyMigrationActor") fuelEconomyMigrationActor: ActorRef) {

  schedule()

  def schedule(): Cancellable = {
    Logger.info(s"Scheduling 'EdmundsMigrationActor' with 15 seconds delay")
    system.scheduler.scheduleOnce(15.second, fuelEconomyMigrationActor, MigrateEdmundsData)
  }
}
