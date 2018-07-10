package services.internal.settings

import dao.settings.SettingsDao
import javax.inject.Inject
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import services.internal.cache.CacheService
import services.internal.services.ServicesService
import services.internal.settings.SettingsService._

import scala.concurrent.Future
import scala.util.Try

class DefaultSettingsService @Inject()(cacheService: CacheService,
                                       settingsDao: SettingsDao,
                                       servicesService: ServicesService) extends SettingsService {

  val logger = Logger(this.getClass)

  override def getPriceSettings: Future[PriceSettings] = {
    (for {
      exteriorCleaning <- servicesService.getExteriorCleaningService
      exteriorAndInterior <- servicesService.getExteriorAndInteriorCleaningService
    } yield (exteriorCleaning, exteriorAndInterior)).map {
      case (exteriorCleaning, exteriorAndInterior) =>
        val carWashingPrice = exteriorCleaning.price
        val interiorCleaningPrice = exteriorAndInterior.price - carWashingPrice
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
      logger.debug(s"Caching setting '$key:${setting.value}'")
      cacheService.set(key, setting.value)
      setting.value
    })
  }
}
