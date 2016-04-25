package controllers

import javax.inject.Inject

import commons.enums.ServerError
import controllers.VehiclesController._
import controllers.base.{BaseController, CRUDOperations}
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.api.mvc.{Action, BodyParsers}
import security.TokenStorage
import services.EdmundsService
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global

class VehiclesController @Inject()(val tokenStorage: TokenStorage,
                                   edmundsService: EdmundsService,
                                   val dbConfigProvider: DatabaseConfigProvider)
  extends BaseController
    with CRUDOperations[VehiclesRow, VehicleDto] {

  val db = dbConfigProvider.get.db

  def getVehiclesMakers = Action.async {
    edmundsService.getCarMakers()
      .map { makersOpt =>
        makersOpt.map(makers => ok(makers.makes))
          .getOrElse(badRequest("Can't load makers data", ServerError))
      }
  }

  def create = authorized.async(BodyParsers.parse.json) { implicit request =>
    processRequest[VehicleDto](request.body) { dto =>
      val userId = request.token.get.userInfo.id
      val createQuery = Vehicles.map(v => (v.makerId, v.makerNiceName, v.modelId, v.modelNiceName, v.yearId,
        v.year, v.color, v.licPlate, v.userId)) returning Vehicles.map(_.id) +=(dto.makerId, dto.makerName,
        dto.modelId, dto.modelName, dto.yearId, dto.year, dto.color.getOrElse("None"), dto.licPlate, userId)

      db.run(createQuery).map(vehiclesId => created(routes.VehiclesController.get(vehiclesId).absoluteURL()))
    }(vehicleDtoFormat)
  }

  def update(id: Int) = authorized.async(parse.json) { request =>
    val userId = request.token.get.userInfo.id
    val existQuery = for {
      l <- Vehicles if l.userId === userId && l.id === id
    } yield l
    db.run(existQuery.length.result).flatMap {
      case 1 =>
        processRequest[VehicleDto](request.body) { dto =>
          val updateQuery = Vehicles.filter(_.id === id).map(v => (v.makerId, v.makerNiceName, v.modelId,
            v.modelNiceName, v.year, v.yearId, v.color, v.licPlate))
            .update(dto.makerId, dto.makerName, dto.modelId, dto.modelName, dto.year, dto.yearId,
              dto.color.getOrElse("None"), dto.licPlate)
          db.run(updateQuery).map(r => ok("Updated"))
        }(vehicleDtoFormat)
      case _ => wrapInFuture(notFound)
    }
  }

  override def toDto(vehicle: _root_.models.Tables.VehiclesRow): VehicleDto = {
    new VehicleDto(Some(vehicle.id), vehicle.makerId, vehicle.makerNiceName, vehicle.modelId,
      vehicle.modelNiceName, vehicle.yearId, vehicle.year, Option(vehicle.color), vehicle.licPlate)
  }

  override val table = Vehicles
}

object VehiclesController {

  case class VehicleDto(id: Option[Int],
                        makerId: Int,
                        makerName: String,
                        modelId: String,
                        modelName: String,
                        yearId: Int,
                        year: Int,
                        color: Option[String],
                        licPlate: Option[String])

  implicit val vehicleDtoFormat = Json.format[VehicleDto]
}


