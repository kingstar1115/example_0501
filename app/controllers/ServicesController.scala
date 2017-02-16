package controllers

import javax.inject.Inject

import controllers.base.BaseController
import play.api.libs.json.{Json, Writes}
import security.TokenStorage
import services.internal.services.ServicesService
import services.internal.services.ServicesService._

import scala.concurrent.ExecutionContext.Implicits.global

class ServicesController @Inject()(val tokenStorage: TokenStorage,
                                   servicesService: ServicesService) extends BaseController {

  implicit val extraWrites: Writes[ExtraDto] = Json.writes[ExtraDto]
  implicit val serviceWrites: Writes[ServiceDto] = Json.writes[ServiceDto]
  implicit val servicesWrites: Writes[ServicesWithExtrasDto] = Json.writes[ServicesWithExtrasDto]

  def getServices(version: String) = authorized.async { _ =>
    servicesService.getAllServicesWithExtras().map(ok(_))
  }
}
