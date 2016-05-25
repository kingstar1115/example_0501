package controllers

import com.google.inject.Inject
import controllers.LocationController._
import controllers.base.{BaseController, CRUDOperations}
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, Json, Reads}
import security.TokenStorage
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global

class LocationController @Inject()(val tokenStorage: TokenStorage,
                                   val dbConfigProvider: DatabaseConfigProvider)
  extends BaseController
    with CRUDOperations[LocationsRow, LocationDto] {

  val db = dbConfigProvider.get.db

  def create = authorized.async(parse.json) { implicit request =>
    processRequest[LocationDto](request.body) { dto =>
      val userId = request.token.get.userInfo.id
      val createQuery = Locations.map(l => (l.userId, l.title, l.formattedAddress, l.latitude,
        l.longitude, l.address, l.apartments, l.zipCode, l.notes)) returning Locations.map(_.id) +=(userId, dto.title,
        dto.formattedAddress, dto.latitude, dto.longitude, dto.address, dto.apartments, dto.zipCode, dto.notes)

      db.run(createQuery).map(locationId => created(routes.LocationController.get(locationId).absoluteURL()))
    }
  }

  def update(id: Int) = authorized.async(parse.json) { request =>
    val userId = request.token.get.userInfo.id
    val existQuery = for {
      l <- Locations if l.userId === userId && l.id === id
    } yield l
    db.run(existQuery.length.result).flatMap {
      case 1 =>
        processRequest[LocationDto](request.body) { dto =>
          val updateQuery = Locations.filter(_.id === id).map(l => (l.title, l.address, l.latitude, l.longitude))
            .update(dto.title, dto.address, dto.latitude, dto.longitude)
          db.run(updateQuery).map(r => ok("Updated"))
        }
      case _ => wrapInFuture(notFound)
    }
  }

  override def toDto(location: _root_.models.Tables.LocationsRow): LocationDto = {
    new LocationDto(Some(location.id), location.title, location.address, location.apartments,
      location.zipCode, location.formattedAddress, location.latitude, location.longitude, location.notes)
  }

  override val table = Locations
}

object LocationController {

  case class LocationDto(id: Option[Int] = None,
                         title: String,
                         address: Option[String],
                         apartments: Option[String],
                         zipCode: Option[String],
                         formattedAddress: String,
                         latitude: BigDecimal,
                         longitude: BigDecimal,
                         notes: Option[String])

  implicit val locationWrites = Json.writes[LocationDto]
  implicit val locationReads: Reads[LocationDto] = (
    (JsPath \ "id").readNullable[Int] and
      (JsPath \ "title").read[String](maxLength[String](255)) and
      (JsPath \ "address").readNullable[String](maxLength[String](255)) and
      (JsPath \ "apartments").readNullable[String](maxLength[String](10)) and
      (JsPath \ "zipCode").readNullable[String](maxLength[String](6)) and
      (JsPath \ "formattedAddress").read[String](maxLength[String](255)) and
      (JsPath \ "latitude").read[BigDecimal] and
      (JsPath \ "longitude").read[BigDecimal] and
      (JsPath \ "notes").readNullable[String]
    ) (LocationDto.apply _)
}
