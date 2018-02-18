package config

import actors.schedulers.TimeSlotGenerationScheduler
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

  private def bindSchedulers(): Unit = {
    bind(classOf[TimeSlotGenerationScheduler]).asEagerSingleton()
  }

  private def bindActors(): Unit = {
    bindActor[TimeSlotGenerationActor]("timeSlotGenerationActor")
    bindActor[TaskTimeSlotMigrationActor]("taskTimeSlotMigrationActor")
  }
}
