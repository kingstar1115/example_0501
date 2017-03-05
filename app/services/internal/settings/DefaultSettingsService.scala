package services.internal.settings

import javax.inject.Inject

import dao.settings.SettingsDao
import play.api.Logger
import services.internal.cache.CacheService
import services.internal.settings.SettingsService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class DefaultSettingsService @Inject()(cacheService: CacheService,
                                       settingsDao: SettingsDao) extends SettingsService {

  initializeSettings()

  override def initializeSettings(): Unit = {
    Logger.info("Initializing project settings")
    settingsDao.loadAll.map { settings =>
      val priceSettings = PriceSettings(
        settings.find(_.key == "carWashing").get.value.toInt,
        settings.find(_.key == "interiorCleaning").get.value.toInt
      )
      cacheService.set(pricesSettingsKey, priceSettings)
    }
  }

  override def getPriceSettings: PriceSettings = {
    cacheService.get[PriceSettings](pricesSettingsKey).get
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
