package controllers

import javax.inject.Inject

import controllers.base.BaseController
import play.api.mvc.Action
import security.TokenStorage
import services.internal.settings.SettingsService


class SettingsController @Inject()(val tokenStorage: TokenStorage,
                                   settingsService: SettingsService) extends BaseController {

  def getSettings(version: String) = Action { _ =>
    ok(settingsService.getPriceSettings)
  }
}
