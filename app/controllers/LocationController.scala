package controllers

import com.google.inject.Inject
import controllers.LocationController._
import controllers.base.BaseController
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import security.TokenStorage
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global

class LocationController @Inject()(val tokenStorage: TokenStorage,
                                   dbConfigProvider: DatabaseConfigProvider) extends BaseController {

  implicit val locationFormat = Json.format[LocationDto]

  val db = dbConfigProvider.get.db

  def create = authorized.async(parse.json) { implicit request =>
    val onValidJson = (dto: LocationDto) => {
      val userId = request.token.get.userInfo.id
      val createQuery = Locations.map(location => (location.userId, location.title, location.address, location.latitude,
        location.longitude)) returning Locations.map(_.id) +=(userId, dto.title, dto.address, dto.latitude, dto.longitude)
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
      locationOpt.map { location =>
        val dto = LocationDto(Some(location.id), location.title, location.address, location.latitude, location.longitude)
        ok(dto)
      }.getOrElse(notFound)
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
}

object LocationController {

  case class LocationDto(id: Option[Int] = None,
                         title: String,
                         address: String,
                         latitude: BigDecimal,
                         longitude: BigDecimal)

}
