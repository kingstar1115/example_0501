package controllers.rest

import javax.inject.Inject

import commons.enums.InternalSError
import controllers.rest.VehiclesController._
import controllers.rest.base._
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Json
import play.api.mvc.Action
import security.TokenStorage
import services.EdmundsService
import slick.driver.PostgresDriver.api._

//noinspection TypeAnnotation
class VehiclesController @Inject()(val tokenStorage: TokenStorage,
                                   edmundsService: EdmundsService,
                                   val dbConfigProvider: DatabaseConfigProvider)
  extends BaseController
    with CRUDOperations[VehiclesRow, VehicleDto] {

  val db = dbConfigProvider.get.db

  def getVehiclesMakers(version: String) = Action.async {
    edmundsService.getCarMakers()
      .map { makersOpt =>
        makersOpt
          .map(makers => ok(makers.makes))
          .getOrElse(badRequest("Can't load makers data", InternalSError))
      }
  }

  def create(version: String) = authorized.async(parse.json) { implicit request =>
    processRequestF[VehicleDto](request.body) { dto =>
      val userId = request.token.get.userInfo.id
      val createQuery = Vehicles.map(v => (v.makerId, v.makerNiceName, v.modelId, v.modelNiceName, v.yearId,
        v.year, v.color, v.licPlate, v.userId)) returning Vehicles.map(_.id) += (dto.makerId, dto.makerName,
        dto.modelId, dto.modelName, dto.yearId, dto.year, dto.color.getOrElse("None"), dto.licPlate, userId)

      db.run(createQuery)
        .map(vehiclesId => created(routes.VehiclesController.get(version, vehiclesId).absoluteURL()))
    }(vehicleDtoFormat)
  }

  def update(version: String, id: Int) = authorized.async(parse.json) { request =>
    processRequestF[VehicleDto](request.body) { dto =>
      val userId = request.token.get.userInfo.id
      val updateQuery = Vehicles.filter(v => v.id === id && v.userId === userId && v.deleted === false)
        .map(v => (v.makerId, v.makerNiceName, v.modelId, v.modelNiceName, v.year, v.yearId, v.color, v.licPlate))
        .update(dto.makerId, dto.makerName, dto.modelId, dto.modelName, dto.year, dto.yearId,
          dto.color.getOrElse("None"), dto.licPlate)
      db.run(updateQuery).map {
        case 1 => success
        case _ => notFound
      }
    }
  }

  override def delete(version: String, id: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val deleteQuery = for {
      v <- Vehicles if v.userId === userId && v.id === id && v.deleted === false
    } yield v.deleted
    db.run(deleteQuery.update(true)).map {
      case 1 => success
      case _ => notFound
    }
  }

  override def findByIdAndUserId(id: Int, userId: Int) = {
    for {
      v <- Vehicles if v.id === id && v.userId === userId && v.deleted === false
    } yield v
  }


  override def findByUser(userId: Int) = {
    for {
      v <- Vehicles if v.userId === userId && v.deleted === false
    } yield v
  }

  override def toDto(vehicle: _root_.models.Tables.VehiclesRow): VehicleDto = {
    VehicleDto.convert(vehicle)
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

  object VehicleDto {
    def convert(vehicle: VehiclesRow): VehicleDto = {
      VehicleDto(Some(vehicle.id), vehicle.makerId, vehicle.makerNiceName, vehicle.modelId,
        vehicle.modelNiceName, vehicle.yearId, vehicle.year, Option(vehicle.color), vehicle.licPlate)
    }
  }

  implicit val vehicleDtoFormat = Json.format[VehicleDto]
}


