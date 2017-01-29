package dao.users

import com.google.inject.Inject
import dao.vehicles.VehiclesDao
import models.Tables._
import slick.driver.PostgresDriver.api._


class DefaultUserDao @Inject()(vehicleDao: VehiclesDao) extends UsersDao {

  override def findById(id: Int): Query[Users, UsersRow, Seq] = Users.filter(_.id === id)

  override def findByIdWithVehicle(id: Int, vehicleId: Int): Query[(Users, Vehicles), (UsersRow, VehiclesRow), Seq] = findById(id)
    .join(vehicleDao.findById(vehicleId)).on(_.id === _.userId)
}
