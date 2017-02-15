package services.internal.services

import com.google.inject.ImplementedBy
import models.Tables.ServicesRow
import services.internal.services.ServicesService.ServicesWithExtrasDto

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultServicesService])
trait ServicesService {

  def hasInteriorCleaning(service: ServicesRow): Boolean

  def loadServicesWithExtras(): Future[ServicesWithExtrasDto]
}

object ServicesService {

  case class ExtraDto(id: Int, name: String, price: Int)

  case class ServiceDto(id: Int, name: String, description: Option[String], price: Int, extras: Set[Int])

  case class ServicesWithExtrasDto(services: Seq[ServiceDto], extras: Seq[ExtraDto])

}
