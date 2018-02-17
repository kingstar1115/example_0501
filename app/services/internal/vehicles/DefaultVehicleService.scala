package services.internal.vehicles

import javax.inject.Inject

import dao.SlickDbService
import dao.vehicles.{VehicleQueryObject, VehiclesDao}
import models.Tables.VehiclesRow
import play.api.libs.concurrent.Execution.Implicits._
import services.external.vehicles.VehicleDataService
import services.external.vehicles.VehicleDataService.VehicleModel
import services.internal.vehicles.DefaultVehicleService._

import scala.concurrent.Future


class DefaultVehicleService @Inject()(vehicleDao: VehiclesDao,
                                      slickDbService: SlickDbService,
                                      vehicleDataService: VehicleDataService) extends VehiclesService {

  override def findById(id: Int): Future[VehiclesRow] = {
    slickDbService.findOne(VehicleQueryObject.findByIdQuery(id))
  }

  override def addAdditionalPrice(id: Int, userId: Int): Future[Boolean] = {
    vehicleDao.findByIdAndUser(id, userId)
      .map {
        case Some(vehicle) =>
          isLargeVehicle(vehicle.source, vehicle.vehicleSizeClass)
        case None =>
          throw new IllegalArgumentException("Can't find car for this user")
      }
  }

  override def addAdditionalPrice(make: String, model: String, year: Int): Future[Boolean] = {
    vehicleDataService.getVehicleSize(VehicleModel(year, make, model))
      .map(vehicleSize => isLargeVehicle(Some(vehicleSize.provider), vehicleSize.body))
  }

  def isLargeVehicle(sourceOption: Option[String], vehicleSizeOption: Option[String]): Boolean = {
    (for {
      source <- sourceOption
      vehicleSize <- vehicleSizeOption
    } yield (source, vehicleSize)).exists {
      case (source: String, vehicleSize: String) =>
        source match {
          case "Edmunds" =>
            !vehicleSize.equalsIgnoreCase("Car")
          case _ =>
            !EPASmallVehicleSizes.contains(vehicleSize)
        }
    }
  }
}

object DefaultVehicleService {

  val EdmundsDataProvider = "Edmunds"
  val FuelEconomyDataProvider = "Fueleconomy"

  val EPASmallVehicleSizes = Seq(
    "Two Seaters",
    "Minicompact Cars",
    "Subcompact Cars",
    "Compact Cars",
    "Midsize Cars",
    "Large Cars",
    "Small Station Wagons",
    "Midsize Station Wagons"
  )
}
