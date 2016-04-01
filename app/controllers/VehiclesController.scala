package controllers

import javax.inject.Inject

import commons.enums.ServerError
import controllers.VehiclesController._
import controllers.base.{BaseController, ListResponse}
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
                                   dbConfigProvider: DatabaseConfigProvider) extends BaseController {

  val db = dbConfigProvider.get.db

  def getVehiclesMakers = Action.async {
    edmundsService.getCarMakers()
      .map { makersOpt =>
        makersOpt.map(makers => ok(makers.makes))
          .getOrElse(badRequest("Can't load makers data", ServerError))
      }
  }

  def create = authorized.async(BodyParsers.parse.json) {implicit request =>

    def onValidJson(dto: VehicleDto) = {
      val userId = request.token.get.userInfo.id
      val createQuery = Vehicles.map(v => (v.makerId, v.makerNiceName, v.modelId, v.modelNiceName, v.yearId,
        v.year, v.color, v.licPlate, v.userId)) returning Vehicles.map(_.id) +=(dto.makerId, dto.makerName,
        dto.modelId, dto.modelName, dto.yearId, dto.year, dto.color, dto.licPlate, userId)
      db.run(createQuery).map { vehiclesId =>
        val resourceUrl = routes.VehiclesController.get(vehiclesId).absoluteURL()
        created(resourceUrl)
      }
    }

    request.body.validate[VehicleDto](vehicleDtoFormat)
      .fold((errors) => wrapInFuture(jsonValidationFailed(errors)), (dto) => onValidJson(dto))
  }

  def get(id: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val selectQuery = for {
      l <- Vehicles if l.userId === userId
    } yield l
    db.run(selectQuery.result.headOption).map { locationOpt =>
      locationOpt.map(location => ok(location.toDto)).getOrElse(notFound)
    }
  }

  def delete(id: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val deleteQuery = for {
      l <- Vehicles if l.userId === userId && l.id === id
    } yield l
    db.run(deleteQuery.delete).map {
      case 1 => ok("Success")
      case _ => notFound
    }
  }

  def list(offset: Int, limit: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val selectQuery = for {
      l <- Vehicles if l.userId === userId
    } yield l
    db.run(selectQuery.length.result zip selectQuery.take(limit).drop(offset).result).map { result =>
      val dtos = result._2.map(_.toDto)
      ok(ListResponse(dtos, limit, offset, result._1))
    }
  }

  def update(id: Int) = authorized.async(parse.json) { request =>
    val userId = request.token.get.userInfo.id
    val existQuery = for {
      l <- Vehicles if l.userId === userId && l.id === id
    } yield l
    db.run(existQuery.length.result).flatMap {
      case 1 =>
        val onValidJson = (dto: VehicleDto) => {
          val updateQuery = Vehicles.filter(_.id === id).map(v => (v.makerId, v.makerNiceName, v.modelId,
            v.modelNiceName, v.year, v.yearId, v.color, v.licPlate ))
            .update(dto.makerId, dto.makerName, dto.modelId, dto.modelName, dto.year, dto.yearId, dto.color, dto.licPlate)
          db.run(updateQuery).map(r => ok("Updated"))
        }
        request.body.validate[VehicleDto]
          .fold((errors) => wrapInFuture(jsonValidationFailed(errors)), (dto) => onValidJson(dto))
      case _ => wrapInFuture(notFound)
    }
  }
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

  implicit class VehicleExt(vehicle: VehiclesRow) {

    def toDto = {
      new VehicleDto(Some(vehicle.id), vehicle.makerId, vehicle.makerNiceName, vehicle.modelId,
        vehicle.modelNiceName, vehicle.yearId, vehicle.year, vehicle.color, vehicle.licPlate)
    }
  }

}


