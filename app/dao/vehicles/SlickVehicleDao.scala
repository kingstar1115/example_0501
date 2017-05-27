package dao.vehicles

import javax.inject.Inject

import dao.SlickDbService
import models.Tables._
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future

class SlickVehicleDao @Inject()(slickDbService: SlickDbService) extends VehiclesDao {

  def findById(id: Int): Future[VehiclesRow] =
    slickDbService.run(VehicleQueryObject.withId(id).result.head)

  override def findByIdAndUser(id: Int, userId: Int): Future[Option[VehiclesRow]] = {
    slickDbService.run(VehicleQueryObject.withIdAndOwner(id, userId).result.headOption)
  }
}
