package dao.vehicles

import models.Tables._
import slick.driver.PostgresDriver.api._
import slick.lifted.Query

class DefaultVehicleDao extends VehiclesDao {
  override def findById(id: Int): Query[Vehicles, VehiclesRow, Seq] = Vehicles.filter(_.id === id)
}
