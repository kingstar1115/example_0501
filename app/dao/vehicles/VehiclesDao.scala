package dao.vehicles

import com.google.inject.ImplementedBy
import models.Tables._
import slick.lifted.Query

@ImplementedBy(classOf[DefaultVehicleDao])
trait VehiclesDao {

  def findById(id: Int): Query[Vehicles, VehiclesRow, Seq]

}
