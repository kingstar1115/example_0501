package services.internal.vehicles

import javax.inject.Inject

import dao.vehicles.VehiclesDao
import models.Tables.VehiclesRow
import services.EdmundsService
import services.EdmundsService.Style
import services.internal.vehicles.DefaultVehicleService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class DefaultVehicleService @Inject()(vehicleDao: VehiclesDao,
                                      edmundsService: EdmundsService) extends VehiclesService {

  override def findById(id: Int): Future[VehiclesRow] = vehicleDao.findById(id)

  override def addAdditionalPrice(id: Int): Future[Boolean] = {
    addAdditionalPriceInternal(vehicleDao.findById(id)
      .flatMap(vehicle => edmundsService.getCarStyle(vehicle.makerNiceName, vehicle.modelNiceName, vehicle.year))
    )
  }

  override def addAdditionalPrice(make: String, model: String, year: Int): Future[Boolean] = {
    addAdditionalPriceInternal(edmundsService.getCarStyle(make, model, year))
  }

  private def addAdditionalPriceInternal(styleSupplier: => Future[Option[Style]]): Future[Boolean] = {
    styleSupplier.map {
      case Some(style) =>
        !carVehicleType.equalsIgnoreCase(style.categories.vehicleType)
      case _ =>
        false
    }
  }
}

object DefaultVehicleService {
  private val carVehicleType = "Car"
}
