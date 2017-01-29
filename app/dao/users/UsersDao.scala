package dao.users

import com.google.inject.ImplementedBy
import models.Tables._
import slick.lifted.Query


@ImplementedBy(classOf[DefaultUserDao])
trait UsersDao {

  def findById(id: Int): Query[Users, UsersRow, Seq]

  def findByIdWithVehicle(id: Int, vehicleId: Int): Query[(Users, Vehicles), (UsersRow, VehiclesRow), Seq]
}
