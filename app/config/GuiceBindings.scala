package config

import com.google.inject.AbstractModule
import services.internal.notifications.{APNotificationService, PushNotificationService}
import services.internal.settings.{DefaultSettingsService, SettingsService}


class GuiceBindings extends AbstractModule {

  override def configure() = {
    bind(classOf[PushNotificationService]).to(classOf[APNotificationService]).asEagerSingleton()
    bind(classOf[SettingsService]).to(classOf[DefaultSettingsService]).asEagerSingleton()
  }
}
