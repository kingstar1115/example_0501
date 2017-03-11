package services.internal.settings

import javax.inject.Inject

import dao.settings.SettingsDao
import play.api.Logger
import services.internal.cache.CacheService
import services.internal.services.ServicesService
import services.internal.settings.SettingsService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class DefaultSettingsService @Inject()(cacheService: CacheService,
                                       settingsDao: SettingsDao,
                                       servicesService: ServicesService) extends SettingsService {

  override def getPriceSettings: Future[PriceSettings] = {
    val servicesFuture = for {
      exteriorCleaning <- servicesService.getExteriorCleaningService
      exteriorAndInterior <- servicesService.getExteriorAndInteriorCleaningService
    } yield (exteriorCleaning, exteriorAndInterior)
    servicesFuture.map { tuple =>
      val carWashingPrice = tuple._1.price
      val interiorCleaningPrice = tuple._2.price - carWashingPrice
      PriceSettings(carWashingPrice, interiorCleaningPrice)
    }
  }

  override def getBasePrice: Future[BasePrice] = {
    servicesService.getExteriorCleaningService.map(_.price).map(BasePrice.apply)
  }

  override def getIntValue(key: String, defaultValue: Int): Future[Int] = {
    loadValueFromCache(key).map {
      case Some(value) =>
        Try(value.toInt)
          .getOrElse(defaultValue)
      case _ =>
        defaultValue
    }
  }

  private def loadValueFromCache(key: String): Future[Option[String]] = {
    cacheService.get[String](key)
      .fold(loadValue(key))(value => Future.successful(Option(value)))
  }

  private def loadValue(key: String): Future[Option[String]] = {
    settingsDao.findByKey(key).map(_.map { setting =>
      Logger.info(s"Caching setting '$key:${setting.value}'")
      cacheService.set(key, setting.value)
      setting.value
    })
  }
}
