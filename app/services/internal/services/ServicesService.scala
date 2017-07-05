package services.internal.services

import com.google.inject.ImplementedBy
import commons.ServerError
import dao.services.ServicesDao.ServiceWithExtras
import models.Tables.{ExtrasRow, ServicesExtrasRow, ServicesRow}
import services.internal.services.ServicesService.ServicesWithExtrasDto

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultServicesService])
trait ServicesService {

  def getExteriorCleaningService: Future[ServicesRow]

  def getExteriorAndInteriorCleaningService: Future[ServicesRow]

  def getServiceWithExtras(id: Int, extras: Set[Int]): Future[ServiceWithExtras]

  def hasInteriorCleaning(service: ServicesRow): Boolean

  def getAllServicesWithExtras(vehicleId: Int, userId: Int): Future[Either[ServerError, ServicesWithExtrasDto]]

  def getAllServicesWithExtras(make: String, model: String, year: Int): Future[ServicesWithExtrasDto]

  def getServicePrice(service: ServicesRow, make: String, model: String, year: Int): Future[Int]
}

object ServicesService {

  val ExteriorCleaning = "EXTERIOR_CLEANING"
  val ExteriorAndInteriorCleaning = "EXTERIOR_AND_INTERIOR_CLEANING"
  val DefaultAdditionalCost = 1000

  case class ExtraDto(id: Int, name: String, description: Option[String], price: Int)

  case class ServiceDto(id: Int, name: String, description: Option[String], price: Int, extras: Set[Int], seq: Int)

  case class ServicesWithExtrasDto(services: Seq[ServiceDto], extras: Seq[ExtraDto])

  case class ServicesWithExtras(services: Seq[(ServicesRow, Option[ServicesExtrasRow])], extras: Seq[ExtrasRow])

}
