package controllers.rest

import javax.inject.Inject

import controllers.rest.VehiclesController._
import controllers.rest.base._
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{Format, Json}
import play.api.mvc.Action
import security.TokenStorage
import services.external.vehicles.VehicleDataService
import services.external.vehicles.VehicleDataService.{VehicleModel, VehicleSize}
import slick.driver.PostgresDriver.api._

//noinspection TypeAnnotation
class VehiclesController @Inject()(val tokenStorage: TokenStorage,
                                   vehicleDataService: VehicleDataService,
                                   val dbConfigProvider: DatabaseConfigProvider)
  extends BaseController
    with CRUDOperations[VehiclesRow, VehicleDto] {

  val db = dbConfigProvider.get.db

  def getAvailableYears() = Action.async { _ =>
    vehicleDataService.getAvailableYears()
      .map(years => ok(years))
  }

  def getMakesByYear(year: Int) = Action.async { _ =>
    vehicleDataService.getMakesByYear(year)
      .map(makes => ok(makes))
  }

  def getModelsByYearAndMake(year: Int, make: String) = Action.async { _ =>
    vehicleDataService.getModelsByYearAndMake(year, make)
      .map(models => ok(models))
  }

  def create(version: String) = authorized.async(parse.json) { implicit request =>
    processRequestF[VehicleDto](request.body) { dto =>
      def saveVehicle(vehicleBody: VehicleSize) = {
        val userId = request.token.get.userInfo.id
        val createQuery = Vehicles.map(v => (v.makerId, v.makerNiceName, v.modelId, v.modelNiceName, v.yearId,
          v.year, v.color, v.licPlate, v.userId, v.source, v.vehicleSizeClass)) returning Vehicles.map(_.id) += (dto.makerId,
          dto.makerName, dto.modelId, dto.modelName, dto.yearId, dto.year, dto.color.getOrElse("None"), dto.licPlate,
          userId, Some(vehicleBody.provider), vehicleBody.body)
        db.run(createQuery)
      }

      (for {
        body <- vehicleDataService.getVehicleSize(VehicleModel(dto.yearId, dto.makerId, dto.modelId))
        vehicleId <- saveVehicle(body)
      } yield vehicleId)
        .map(vehicleId => created(routes.VehiclesController.get(version, vehicleId).absoluteURL()))
    }
  }

  def update(version: String, id: Int) = authorized.async(parse.json) { request =>
    processRequestF[VehicleDto](request.body) { dto =>
      def update(vehicleBody: VehicleSize) = {
        val userId = request.token.get.userInfo.id
        val updateQuery = Vehicles.filter(v => v.id === id && v.userId === userId && v.deleted === false)
          .map(v => (v.makerId, v.makerNiceName, v.modelId, v.modelNiceName, v.year, v.yearId, v.color, v.licPlate, v.source, v.vehicleSizeClass))
          .update(dto.makerId, dto.makerName, dto.modelId, dto.modelName, dto.year, dto.yearId,
            dto.color.getOrElse("None"), dto.licPlate, Some(vehicleBody.provider), vehicleBody.body)
        db.run(updateQuery)
      }

      (for {
        body <- vehicleDataService.getVehicleSize(VehicleModel(dto.yearId, dto.makerId, dto.modelId))
        count <- update(body)
      } yield count).map {
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
                        makerId: String,
                        makerName: String,
                        modelId: String,
                        modelName: String,
                        yearId: Int,
                        year: Int,
                        color: Option[String],
                        licPlate: Option[String])

  object VehicleDto {

    implicit val JsonFormat: Format[VehicleDto] = Json.format[VehicleDto]

    def convert(vehicle: VehiclesRow): VehicleDto = {
      VehicleDto(Some(vehicle.id), vehicle.makerId, vehicle.makerNiceName, vehicle.modelId,
        vehicle.modelNiceName, vehicle.yearId, vehicle.year, Option(vehicle.color), vehicle.licPlate)
    }
  }

}


