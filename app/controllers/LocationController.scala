package controllers

import com.google.inject.Inject
import controllers.LocationController._
import controllers.base.{BaseController, ListResponse}
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, Json, Reads}
import security.TokenStorage
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global

class LocationController @Inject()(val tokenStorage: TokenStorage,
                                   dbConfigProvider: DatabaseConfigProvider) extends BaseController {

  implicit val locationWrites = Json.writes[LocationDto]
  implicit val locationReads: Reads[LocationDto] = (
      (JsPath \ "id").readNullable[Int] and
      (JsPath \ "title").read[String](maxLength[String](255)) and
      (JsPath \ "address").read[String](maxLength[String](255)) and
      (JsPath \ "apartments").read[String](maxLength[String](10)) and
      (JsPath \ "zipCode").read[String](maxLength[String](6)) and
      (JsPath \ "formattedAddress").read[String](maxLength[String](255)) and
      (JsPath \ "latitude").read[BigDecimal] and
      (JsPath \ "longitude").read[BigDecimal] and
      (JsPath \ "notes").readNullable[String]
    )(LocationDto.apply _)

  val db = dbConfigProvider.get.db

  def create = authorized.async(parse.json) { implicit request =>
    val onValidJson = (dto: LocationDto) => {
      val userId = request.token.get.userInfo.id
      val createQuery = Locations.map(l => (l.userId, l.title, l.formattedAddress, l.latitude,
        l.longitude, l.address, l.appartments, l.zipCode, l.notes)) returning Locations.map(_.id) +=(userId, dto.title,
        dto.formattedAddress, dto.latitude, dto.longitude, dto.address, dto.apartments, dto.zipCode, dto.notes)
      db.run(createQuery).map { locationId =>
        val resourceUrl = routes.LocationController.get(locationId).absoluteURL()
        created(resourceUrl)
      }
    }
    request.body.validate[LocationDto]
      .fold((errors) => wrapInFuture(jsonValidationFailed(errors)), (dto) => onValidJson(dto))
  }

  def get(id: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val selectQuery = for {
      l <- Locations if l.userId === userId
    } yield l
    db.run(selectQuery.result.headOption).map { locationOpt =>
      locationOpt.map(location => ok(location.toDto)).getOrElse(notFound)
    }
  }

  def delete(id: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val deleteQuery = for {
      l <- Locations if l.userId === userId && l.id === id
    } yield l
    db.run(deleteQuery.delete).map {
      case 1 => ok("Success")
      case _ => notFound
    }
  }

  def list(offset: Int, limit: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val selectQuery = for {
      l <- Locations if l.userId === userId
    } yield l
    db.run(selectQuery.length.result zip selectQuery.take(limit).drop(offset).result).map { result =>
      val dtos = result._2.map(_.toDto)
      ok(ListResponse(dtos, limit, offset, result._1))
    }
  }

  def update(id: Int) = authorized.async(parse.json) { request =>
    val userId = request.token.get.userInfo.id
    val existQuery = for {
      l <- Locations if l.userId === userId && l.id === id
    } yield l
    db.run(existQuery.length.result).flatMap {
      case 1 =>
        val onValidJson = (dto: LocationDto) => {
          val updateQuery = Locations.filter(_.id === id).map(l => (l.title, l.address, l.latitude, l.longitude))
            .update(dto.title, dto.address, dto.latitude, dto.longitude)
          db.run(updateQuery).map(r => ok("Updated"))
        }
        request.body.validate[LocationDto]
          .fold((errors) => wrapInFuture(jsonValidationFailed(errors)), (dto) => onValidJson(dto))
      case _ => wrapInFuture(notFound)
    }
  }
}

object LocationController {

  implicit class LocationExt(location: LocationsRow) {

    def toDto = {
      new LocationDto(Some(location.id), location.title, location.address, location.appartments,
        location.zipCode, location.formattedAddress, location.latitude, location.longitude, location.notes)
    }
  }

  case class LocationDto(id: Option[Int] = None,
                         title: String,
                         address: String,
                         apartments: String,
                         zipCode: String,
                         formattedAddress: String,
                         latitude: BigDecimal,
                         longitude: BigDecimal,
                         notes: Option[String])

}
