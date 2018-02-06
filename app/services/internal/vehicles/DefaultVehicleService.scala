package services.internal.vehicles

import javax.inject.Inject

import dao.SlickDbService
import dao.vehicles.{VehicleQueryObject, VehiclesDao}
import models.Tables.VehiclesRow
import play.api.libs.concurrent.Execution.Implicits._
import services.EdmundsService
import services.external.vehicles.VehicleDataService
import services.external.vehicles.VehicleDataService.VehicleModel

import scala.concurrent.Future


class DefaultVehicleService @Inject()(vehicleDao: VehiclesDao,
                                      slickDbService: SlickDbService,
                                      vehicleDataService: VehicleDataService) extends VehiclesService {

  override def findById(id: Int): Future[VehiclesRow] = {
    slickDbService.findOne(VehicleQueryObject.findByIdQuery(id))
  }

  override def addAdditionalPrice(id: Int, userId: Int): Future[Boolean] = {
    vehicleDao.findByIdAndUser(id, userId)
      .flatMap {
        case Some(vehicle) =>
          val vehicleModel = VehicleModel(vehicle.year, vehicle.makerNiceName, vehicle.modelNiceName)
          vehicleDataService.isLargeVehicle(vehicleModel)
        case None =>
          throw new IllegalArgumentException("Can't find car for this user")
      }
  }

  override def addAdditionalPrice(make: String, model: String, year: Int): Future[Boolean] = {
    vehicleDataService.isLargeVehicle(VehicleModel(year, make, model))
  }
}
