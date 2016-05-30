package config

import com.google.inject.AbstractModule
import services.notifications.{INotificationService, PushNotificationService}


class GuiceBindings extends AbstractModule {

  override def configure() = {
    bind(classOf[PushNotificationService]).to(classOf[INotificationService]).asEagerSingleton()
  }
}
