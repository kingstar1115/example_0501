package config

import com.google.inject.AbstractModule
import migrations.TaskTimeSlotMigration
import services.internal.notifications.{APNotificationService, PushNotificationService}


class GuiceBindings extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[PushNotificationService]).to(classOf[APNotificationService]).asEagerSingleton()
    bind(classOf[TaskTimeSlotMigration]).asEagerSingleton()
  }
}
