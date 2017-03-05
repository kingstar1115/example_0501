package dao.users

import com.google.inject.Inject
import dao.vehicles.VehiclesDao
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future


class SlickUserDao @Inject()(dbConfigProvider: DatabaseConfigProvider,
                             vehicleDao: VehiclesDao) extends UsersDao {

  private val db = dbConfigProvider.get.db

  override def findById(id: Int): Query[Users, UsersRow, Seq] = Users.filter(_.id === id)

  override def findByIdWithVehicle(id: Int, vehicleId: Int): Future[(UsersRow, VehiclesRow)] = {
    val selectQuery = findById(id).join(Vehicles.filter(_.id === vehicleId)).on(_.id === _.userId)
    db.run(selectQuery.result.head)
  }
}
