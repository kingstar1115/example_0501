package services.internal.vehicles

import com.google.inject.ImplementedBy
import models.Tables.VehiclesRow

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultVehicleService])
trait VehiclesService {
  def findById(id: Int): Future[VehiclesRow]

  def addAdditionalPrice(id: Int, userId: Int): Future[Boolean]

  def addAdditionalPrice(maker: String, model: String, year: Int): Future[Boolean]
}
