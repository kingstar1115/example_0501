package services.internal.settings

import javax.inject.Inject

import models.Tables._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import services.internal.cache.CacheService
import services.internal.settings.DefaultSettingsService._
import services.internal.settings.SettingsService.PriceSettings
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global

class DefaultSettingsService @Inject()(cacheService: CacheService,
                                       val dbConfigProvider: DatabaseConfigProvider) extends SettingsService {

  initializeSettings()

  override def initializeSettings() = {
    Logger.info("Initializing project settings")
    val listQuery = for {
      settings <- Settings
    } yield settings
    dbConfigProvider.get.db.run(listQuery.result).map { settings =>
      val priceSettings = PriceSettings(
        settings.find(_.key == "compactWashing").get.value.toInt,
        settings.find(_.key == "sedanWashing").get.value.toInt,
        settings.find(_.key == "suvWashing").get.value.toInt,
        settings.find(_.key == "interiorCleaning").get.value.toInt
      )
      cacheService.set(PricesSettingsKey, priceSettings)
    }
  }

  override def getPriceSettings: PriceSettings = {
    cacheService.get[PriceSettings](PricesSettingsKey).get
  }
}

object DefaultSettingsService {
  val PricesSettingsKey = "pricesSettings"
}
