package config

import actors.schedulers.{TaskTimeSlotMigrationScheduler, TimeSlotGenerationScheduler}
import actors.{TaskTimeSlotMigrationActor, TimeSlotGenerationActor}
import com.google.inject.AbstractModule
import play.api.libs.concurrent.AkkaGuiceSupport
import services.internal.notifications.{APNotificationService, PushNotificationService}


class GuiceBindings extends AbstractModule with AkkaGuiceSupport {

  override def configure(): Unit = {
    bind(classOf[PushNotificationService]).to(classOf[APNotificationService]).asEagerSingleton()
    bindActors()
    bindSchedulers()
  }

  private def bindSchedulers() = {
    bind(classOf[TimeSlotGenerationScheduler]).asEagerSingleton()
    bind(classOf[TaskTimeSlotMigrationScheduler]).asEagerSingleton()
  }

  private def bindActors() = {
    bindActor[TimeSlotGenerationActor]("timeSlotGenerationActor")
    bindActor[TaskTimeSlotMigrationActor]("taskTimeSlotMigrationActor")
  }
}
