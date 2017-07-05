package services.internal.services

import javax.inject.Inject

import commons.ServerError
import commons.enums.BadRequest
import dao.services.ServicesDao
import dao.services.ServicesDao.ServiceWithExtras
import models.Tables._
import play.api.libs.concurrent.Execution.Implicits._
import services.internal.services.ServicesService._
import services.internal.settings.SettingsService
import services.internal.vehicles.VehiclesService

import scala.concurrent.Future

class DefaultServicesService @Inject()(servicesDao: ServicesDao,
                                       vehiclesService: VehiclesService,
                                       settingsService: SettingsService) extends ServicesService {

  override def getExteriorCleaningService: Future[ServicesRow] = {
    servicesDao.findByKey(ExteriorCleaning)
  }

  override def getExteriorAndInteriorCleaningService: Future[ServicesRow] = {
    servicesDao.findByKey(ExteriorAndInteriorCleaning)
  }

  override def getServiceWithExtras(id: Int, extras: Set[Int]): Future[ServiceWithExtras] = {
    servicesDao.findByIdWithExtras(id, extras)
  }

  override def hasInteriorCleaning(service: ServicesRow): Boolean =
    service.key.equals(ExteriorAndInteriorCleaning)

  override def getAllServicesWithExtras(vehicleId: Int, userId: Int): Future[Either[ServerError, ServicesWithExtrasDto]] = {
    vehiclesService.addAdditionalPrice(vehicleId, userId)
      .flatMap(addAdditionalPrice => loadServicesWithExtras(addAdditionalPrice).map(Right(_)))
      .recover {
        case e: IllegalArgumentException => Left(ServerError(e.getMessage, Some(BadRequest)))
      }
  }

  override def getAllServicesWithExtras(make: String, model: String, year: Int): Future[ServicesWithExtrasDto] = {
    vehiclesService.addAdditionalPrice(make, model, year)
      .flatMap(addAdditionalPrice => loadServicesWithExtras(addAdditionalPrice))
  }

  private def loadServicesWithExtras(addAdditionalPrice: Boolean): Future[ServicesWithExtrasDto] = {
    servicesDao.loadAllWithExtras.map(ServicesWithExtras.tupled)
      .flatMap { serviceWithExtras =>
        val extrasDto = serviceWithExtras.extras.map(_.convertToExtraDto)
        convertServices(serviceWithExtras.services, addAdditionalPrice)
          .map(servicesDto => ServicesWithExtrasDto(servicesDto.sortBy(_.seq), extrasDto))
      }
  }

  private def convertServices(services: Seq[(ServicesRow, Option[ServicesExtrasRow])], addAdditionalPrice: Boolean): Future[Seq[ServiceDto]] = {
    settingsService.getIntValue(SettingsService.serviceAdditionalCost, DefaultAdditionalCost)
      .flatMap { additionalPrice =>
        val dtos = services.groupBy(_._1)
          .map { entry =>
            val extraIds = entry._2.flatMap(_._2).map(_.extraId).toSet
            entry._1.convertToServiceDto(extraIds, addAdditionalPrice, additionalPrice)
          }.toSeq
        Future.sequence(dtos)
      }
  }

  override def getServicePrice(service: ServicesRow, make: String, model: String, year: Int): Future[Int] = {
    settingsService.getIntValue(SettingsService.serviceAdditionalCost, DefaultAdditionalCost)
      .flatMap(additionalPrice => getServicePriceInternal(service, vehiclesService.addAdditionalPrice(make, model, year), additionalPrice))

  }

  private def getServicePriceInternal(service: ServicesRow, addAdditionalPrice: => Future[Boolean], additionalPrice: Int) = {
    if (service.isCarDependentPrice) {
      addAdditionalPrice.flatMap {
        case true =>
          Future.successful(service.price + additionalPrice)
        case _ =>
          Future.successful(service.price)
      }
    } else {
      Future.successful(service.price)
    }
  }

  implicit class ExtExtrasRow(extra: ExtrasRow) {
    def convertToExtraDto: ExtraDto = ExtraDto(extra.id, extra.name, extra.description, extra.price)
  }

  implicit class ExtServicesRow(service: ServicesRow) {
    def convertToServiceDto(extrasIds: Set[Int], addAdditionalPrice: Boolean, additionalPrice: Int): Future[ServiceDto] = {
      getServicePriceInternal(service, Future.successful(addAdditionalPrice), additionalPrice)
        .map(price => ServiceDto(service.id, service.name, service.description, price, extrasIds, service.sequence))
    }
  }

}

object DefaultServicesService {

}
