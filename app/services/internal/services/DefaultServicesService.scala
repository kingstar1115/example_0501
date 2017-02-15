package services.internal.services

import javax.inject.Inject

import dao.services.ServicesDao
import models.Tables._
import services.internal.services.DefaultServicesService._
import services.internal.services.ServicesService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DefaultServicesService @Inject()(servicesDao: ServicesDao) extends ServicesService {

  val exteriorAndInteriorCleaning = "EXTERIOR_AND_INTERIOR_CLEANING"

  override def hasInteriorCleaning(service: ServicesRow): Boolean =
    service.key.exists(_.equals(exteriorAndInteriorCleaning))

  override def loadServicesWithExtras(): Future[ServicesWithExtrasDto] = {
    servicesDao.loadAllWithExtras
      .map(ServicesWithExtras.tupled)
      .map { serviceWithExtras =>
        val extrasDto = serviceWithExtras.extras.map(_.convertToExtraDto)
        val servicesDto = serviceWithExtras.services
          .groupBy(_._1)
          .map { entry =>
            val extraIds = entry._2.filter(_._2.isDefined).map(_._2.get).map(_.extraId).toSet
            entry._1.convertToServiceDto(extraIds)
          }.toSeq
        ServicesWithExtrasDto(servicesDto, extrasDto)
      }
  }
}

object DefaultServicesService {

  implicit class ExtExtrasRow(extra: ExtrasRow) {
    def convertToExtraDto = ExtraDto(extra.id, extra.name, extra.price)
  }

  implicit class ExtServicesRow(service: ServicesRow) {
    def convertToServiceDto(extrasIds: Set[Int]) = ServiceDto(service.id, service.name, service.description, service.price, extrasIds)
  }

  case class ServicesWithExtras(services: Seq[(ServicesRow, Option[ServicesExtrasRow])], extras: Seq[ExtrasRow])

}
