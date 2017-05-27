package dao.users

import com.google.inject.ImplementedBy
import dao.EntityDao
import models.Tables._

import scala.concurrent.Future


@ImplementedBy(classOf[SlickUserDao])
trait UsersDao extends EntityDao[Users, UsersRow] {

  def findByIdWithVehicle(id: Int, vehicleId: Int): Future[(UsersRow, VehiclesRow)]
}
