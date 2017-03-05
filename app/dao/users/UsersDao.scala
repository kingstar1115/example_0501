package dao.users

import com.google.inject.ImplementedBy
import models.Tables._
import slick.lifted.Query

import scala.concurrent.Future


@ImplementedBy(classOf[SlickUserDao])
trait UsersDao {

  def findById(id: Int): Query[Users, UsersRow, Seq]

  def findByIdWithVehicle(id: Int, vehicleId: Int): Future[(UsersRow, VehiclesRow)]
}
