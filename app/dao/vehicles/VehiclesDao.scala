package dao.vehicles

import com.google.inject.ImplementedBy
import models.Tables._

import scala.concurrent.Future

@ImplementedBy(classOf[SlickVehicleDao])
trait VehiclesDao {

  def findById(id: Int): Future[VehiclesRow]

  def findByIdAndUser(id: Int, userId: Int): Future[Option[VehiclesRow]]
}
