package actors

import javax.inject.Inject

import actors.FuelEconomyMigrationActor.MigrateEdmundsData
import akka.actor.{Actor, ActorSystem}
import models.Tables.{Vehicles, VehiclesRow}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits._
import services.external.vehicles.VehicleDataService
import services.external.vehicles.VehicleDataService.VehicleModel
import services.internal.vehicles.VehiclesService
import slick.driver.JdbcProfile

import scala.concurrent.Future
import scala.concurrent.duration._

class FuelEconomyMigrationActor @Inject()(vehicleDataService: VehicleDataService,
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
                vehicleDataService.getVehicleSize(VehicleModel(vehicle.year, vehicle.makerNiceName, vehicle.modelNiceName))
                  .map(vehicleSize => {
                    val updateQuery = Vehicles.filter(_.id === vehicle.id)
                      .map(vehicle => (vehicle.source, vehicle.vehicleSizeClass))
                      .update(Some("Fueleconomy"), vehicleSize.body)
                    db.run(updateQuery)
                  })
              }
          }
        }
      })
  }


}

object FuelEconomyMigrationActor {

  case object MigrateEdmundsData

}
