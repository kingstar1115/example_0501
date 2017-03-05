package dao.vehicles

import javax.inject.Inject

import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future

class SlickVehicleDao @Inject()(dbConfigProvider: DatabaseConfigProvider) extends VehiclesDao {
  private val db = dbConfigProvider.get.db

  override def findById(id: Int): Future[VehiclesRow] =
    db.run(Vehicles.filter(_.id === id).result.head)
}
