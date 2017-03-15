package dao.vehicles

import javax.inject.Inject

import dao.vehicles.SlickVehicleDao._
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.PostgresDriver.api._
import slick.lifted.TableQuery

import scala.concurrent.Future

class SlickVehicleDao @Inject()(val dbConfigProvider: DatabaseConfigProvider) extends VehiclesDao {

  override def query: TableQuery[Vehicles] = Vehicles

  override def findById(id: Int): Future[VehiclesRow] =
    run(query.withId(id).result.head)

  override def findByIdAndUser(id: Int, userId: Int): Future[Option[VehiclesRow]] = {
    run(query.withId(id).withOwner(userId).result.headOption)
  }
}

object SlickVehicleDao {

  implicit class VehicleQueryBuilder[R[_]](query: Query[Vehicles, VehiclesRow, R]) {
    def withId(id: Int): Query[Vehicles, VehiclesRow, R] = {
      query.filter(_.id === id)
        .filter(_.deleted === false)
    }

    def withOwner(userId: Int): Query[Vehicles, VehiclesRow, R] = {
      query.filter(_.userId === userId)
    }
  }

}
