package controllers

import javax.inject.Inject

import controllers.base.BaseController
import play.api.libs.json.{Json, Writes}
import play.api.mvc.Action
import security.TokenStorage
import services.internal.services.ServicesService
import services.internal.services.ServicesService._

import scala.concurrent.ExecutionContext.Implicits.global

class ServicesController @Inject()(val tokenStorage: TokenStorage,
                                   servicesService: ServicesService) extends BaseController {

  implicit val extraWrites: Writes[ExtraDto] = Json.writes[ExtraDto]
  implicit val serviceWrites: Writes[ServiceDto] = Json.writes[ServiceDto]
  implicit val servicesWrites: Writes[ServicesWithExtrasDto] = Json.writes[ServicesWithExtrasDto]

  def getServicesForRegisteredCustomer(version: String, vehicleId: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    servicesService.getAllServicesWithExtras(vehicleId, userId).map {
      case Right(services) =>
        ok(services)
      case Left(error) =>
        badRequest(error)
    }
  }

  def getServices(version: String, make: String, model: String, year: Int) = Action.async { _ =>
    servicesService.getAllServicesWithExtras(make, model, year).map(ok(_))
  }
}
