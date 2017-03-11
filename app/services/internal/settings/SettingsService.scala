package services.internal.settings

import play.api.libs.json.{Format, Json}
import services.internal.settings.SettingsService._

import scala.concurrent.Future

trait SettingsService {

  def getPriceSettings: Future[PriceSettings]

  def getBasePrice: Future[BasePrice]

  def getIntValue(key: String, defaultValue: Int): Future[Int]
}

object SettingsService {

  val serviceAdditionalCost = "service.additional.cost"

  case class PriceSettings(carWashing: Int,
                           interiorCleaning: Int)

  object PriceSettings {
    implicit val priceSettingsFormat: Format[PriceSettings] = Json.format[PriceSettings]
  }

  case class BasePrice(basePrice: Int)

  object BasePrice {
    implicit val basePriceFormat: Format[BasePrice] = Json.format[BasePrice]
  }

}
