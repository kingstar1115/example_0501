package services.internal.users

import com.google.inject.ImplementedBy
import models.Tables.{UsersRow, VehiclesRow}

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultUserService])
trait UsersService {
  def loadUserWithVehicle[T](id: Int, vehicleId: Int): Future[(UsersRow, VehiclesRow)]
}
