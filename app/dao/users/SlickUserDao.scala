package dao.users

import com.google.inject.Inject
import dao.SlickDriver
import dao.vehicles.VehiclesDao
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.Future


class SlickUserDao @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                             vehicleDao: VehiclesDao) extends UsersDao with SlickDriver {

  import profile.api._

  override def query: TableQuery[_root_.models.Tables.Users] = Users

  override def findByIdWithVehicle(id: Int, vehicleId: Int): Future[(UsersRow, VehiclesRow)] = {
    val selectQuery = findByIdQuery(id)
      .join(vehicleDao.findByIdQuery(vehicleId)).on(_.id === _.userId)
    run(selectQuery.result.head)
  }
}
