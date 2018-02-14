package actors

import javax.inject.Inject

import actors.EdmundsMigrationActor.MigrateEdmundsData
import akka.actor.{Actor, ActorSystem}
import models.Tables.{Vehicles, VehiclesRow}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits._
import services.EdmundsService
import services.internal.vehicles.VehiclesService
import slick.driver.JdbcProfile

import scala.concurrent.Future
import scala.concurrent.duration._

class EdmundsMigrationActor @Inject()(edmundsService: EdmundsService,
                                      vehiclesService: VehiclesService,
                                      val dbConfigProvider: DatabaseConfigProvider,
                                      system: ActorSystem) extends Actor
  with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  override def receive: Receive = {
    case MigrateEdmundsData =>
      db.run(Vehicles.filter(_.source.isEmpty).result).flatMap(vehicles => {
        Future {
          vehicles.zipWithIndex.map {
            case (vehicle: VehiclesRow, index: Int) =>
              system.scheduler.scheduleOnce(index.second) {
                edmundsService.getCarStyle(vehicle.makerNiceName, vehicle.modelNiceName, vehicle.year)
                  .map(style => {
                    val updateQuery = Vehicles.filter(_.id === vehicle.id)
                      .map(vehicle => (vehicle.source, vehicle.vehicleSizeClass))
                      .update(Some("Edmunds"), Some(style.map(_.categories.vehicleType).getOrElse("Car")))
                    db.run(updateQuery)
                  })
              }
          }
        }
      })
  }



}

object EdmundsMigrationActor {

  case object MigrateEdmundsData

}
