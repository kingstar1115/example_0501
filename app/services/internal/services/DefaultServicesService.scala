package services.internal.services

import javax.inject.Inject

import dao.services.ServicesDao
import dao.services.ServicesDao.ServiceWithExtras
import models.Tables._
import services.internal.services.ServicesService._
import services.internal.settings.SettingsService
import services.internal.vehicles.VehiclesService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DefaultServicesService @Inject()(servicesDao: ServicesDao,
                                       vehiclesService: VehiclesService,
                                       settingsService: SettingsService) extends ServicesService {

  override def getExteriorCleaningService: Future[ServicesRow] = {
    servicesDao.findByKey(exteriorCleaning)
  }

  override def getExteriorAndInteriorCleaningService: Future[ServicesRow] = {
    servicesDao.findByKey(exteriorAndInteriorCleaning)
  }

  override def getServiceWithExtras(id: Int, extras: Set[Int]): Future[ServiceWithExtras] = {
    servicesDao.findByIdWithExtras(id, extras)
  }

  override def hasInteriorCleaning(service: ServicesRow): Boolean =
    service.key.exists(_.equals(exteriorAndInteriorCleaning))

  override def getAllServicesWithExtras(vehicleId: Int): Future[ServicesWithExtrasDto] = {
    loadServicesWithExtras(vehiclesService.addAdditionalPrice(vehicleId))
  }

  override def getAllServicesWithExtras(make: String, model: String, year: Int) = {
    loadServicesWithExtras(vehiclesService.addAdditionalPrice(make, model, year))
  }

  private def loadServicesWithExtras(addAdditionalPrice: => Future[Boolean]) = {
    val servicesFuture: Future[(ServicesWithExtras, Boolean)] = for {
      services <- servicesDao.loadAllWithExtras.map(ServicesWithExtras.tupled)
      addAdditionalCost <- addAdditionalPrice
    } yield (services, addAdditionalCost)

    servicesFuture.flatMap { tuple =>
      val serviceWithExtras = tuple._1
      val extrasDto = serviceWithExtras.extras.map(_.convertToExtraDto)
      convertServices(serviceWithExtras.services, tuple._2)
        .map(servicesDto => ServicesWithExtrasDto(servicesDto, extrasDto))
    }
  }

  private def convertServices(services: Seq[(ServicesRow, Option[ServicesExtrasRow])], addAdditionalCost: Boolean): Future[Seq[ServiceDto]] = {
    val dtos = services.groupBy(_._1)
      .map { entry =>
        val extraIds = entry._2.flatMap(_._2).map(_.extraId).toSet
        entry._1.convertToServiceDto(extraIds, addAdditionalCost)
      }.toSeq
    Future.sequence(dtos)
  }

  override def getServicePrice(service: ServicesRow, make: String, model: String, year: Int): Future[Int] = {
    getServicePriceInternal(service, vehiclesService.addAdditionalPrice(make, model, year))
  }

  private def getServicePriceInternal(service: ServicesRow, addAdditionalPrice: => Future[Boolean]) = {
    if (service.isCarDependentPrice) {
      addAdditionalPrice.flatMap {
        case true =>
          settingsService.getIntValue(SettingsService.serviceAdditionalCost, defaultAdditionalCost)
            .map(additionalCost => service.price + additionalCost)
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
    def convertToServiceDto(extrasIds: Set[Int], addAdditionalCost: Boolean): Future[ServiceDto] = {
      getServicePriceInternal(service, Future.successful(addAdditionalCost))
        .map(price => ServiceDto(service.id, service.name, service.description, price, extrasIds))
    }
  }

}
