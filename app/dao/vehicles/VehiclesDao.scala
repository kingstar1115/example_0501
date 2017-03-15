package dao.vehicles

import com.google.inject.ImplementedBy
import dao.EntityDao
import models.Tables._

import scala.concurrent.Future

@ImplementedBy(classOf[SlickVehicleDao])
trait VehiclesDao extends EntityDao[Vehicles, VehiclesRow] {

  def findByIdAndUser(id: Int, userId: Int): Future[Option[VehiclesRow]]
}
