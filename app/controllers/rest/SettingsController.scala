package controllers.rest

import javax.inject.Inject

import controllers.rest.base._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Action
import security.TokenStorage
import services.internal.settings.SettingsService


//noinspection TypeAnnotation
class SettingsController @Inject()(val tokenStorage: TokenStorage,
                                   settingsService: SettingsService) extends BaseController {

  def getSettings(version: String) = Action.async { _ =>
    version match {
      case "v3" =>
        settingsService.getBasePrice
          .map(basePrice => ok(basePrice))
      case _ =>
        settingsService.getPriceSettings
          .map(priceSettings => ok(priceSettings))
    }
  }
}
