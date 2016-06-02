package services.internal.settings

import play.api.libs.json.Json
import services.internal.settings.SettingsService._

trait SettingsService {

  def initializeSettings(): Unit

  def getPriceSettings: PriceSettings
}

object SettingsService {

  case class PriceSettings(compactWashing: Int,
                           sedanWashing: Int,
                           suvWashing: Int,
                           interiorCleaning: Int)

  implicit val priceSettingsFormat = Json.format[PriceSettings]
}
