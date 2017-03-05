package services.internal.settings

import play.api.libs.json.{Format, Json}
import services.internal.settings.SettingsService._

import scala.concurrent.Future

trait SettingsService {

  def initializeSettings(): Unit

  def getPriceSettings: PriceSettings

  def getIntValue(key: String, defaultValue: Int): Future[Int]
}

object SettingsService {

  val pricesSettingsKey = "pricesSettings"
  val serviceAdditionalCost = "service.additional.cost"

  case class PriceSettings(carWashing: Int,
                           interiorCleaning: Int)

  object PriceSettings {
    implicit val priceSettingsFormat: Format[PriceSettings] = Json.format[PriceSettings]
  }

}
