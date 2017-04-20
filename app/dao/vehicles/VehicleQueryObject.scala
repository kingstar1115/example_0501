package dao.vehicles

import dao.{QueryObject, SlickDriver}
import models.Tables._

object VehicleQueryObject extends QueryObject[Vehicles, VehiclesRow] with SlickDriver {

  import profile.api._

  val query = Vehicles

  implicit class VehicleQueryObjectExt[R[_]](queryObject: QueryObject[Vehicles, VehiclesRow]) {
    def withId(id: Int): Query[Vehicles, VehiclesRow, Seq] = {
      queryObject.query.filter(_.id === id)
        .filter(_.deleted === false)
    }

    def withIdAndOwner(id: Int, ownerId: Int): Query[Vehicles, VehiclesRow, Seq] = {
      withId(id).filter(_.userId === ownerId)
    }
  }

}
