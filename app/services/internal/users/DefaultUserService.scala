package services.internal.users

import com.google.inject.Inject
import dao.users.UsersDao
import models.Tables.{UsersRow, VehiclesRow}
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.Future

class DefaultUserService @Inject()(usersDao: UsersDao,
                                   dbConfigProvider: DatabaseConfigProvider) extends UsersService {

  override def loadUserWithVehicle[T](id: Int, vehicleId: Int): Future[(UsersRow, VehiclesRow)] = {
    usersDao.findByIdWithVehicle(id, vehicleId)
  }
}
