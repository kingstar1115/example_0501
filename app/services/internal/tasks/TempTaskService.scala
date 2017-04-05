package services.internal.tasks

import java.sql.Timestamp
import javax.inject.Inject

import akka.actor.ActorSystem
import com.stripe.model.Charge
import commons.ServerError
import commons.enums.{StripeError, TookanError}
import commons.monads.transformers.EitherT
import models.Tables.{TimeSlotsRow, _}
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import services.StripeService.ErrorResponse
import services.TookanService.AppointmentResponse
import services.internal.bookings.BookingService
import services.internal.notifications.PushNotificationService
import services.internal.services.ServicesService
import services.internal.settings.SettingsService
import services.internal.tasks.DefaultTaskService._
import services.internal.tasks.TasksService._
import services.internal.tasks.TempTaskService._
import services.internal.users.UsersService
import services.{StripeService, TookanService}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class TempTaskService @Inject()(tookanService: TookanService,
                                settingsService: SettingsService,
                                stripeService: StripeService,
                                dbConfigProvider: DatabaseConfigProvider,
                                system: ActorSystem,
                                pushNotificationService: PushNotificationService,
                                servicesService: ServicesService,
                                usersService: UsersService,
                                bookingService: BookingService)
  extends DefaultTaskService(tookanService: TookanService,
    settingsService: SettingsService,
    stripeService: StripeService,
    dbConfigProvider: DatabaseConfigProvider,
    system: ActorSystem,
    pushNotificationService: PushNotificationService,
    servicesService: ServicesService,
    usersService: UsersService) {

  override def createTaskForCustomer(implicit appointmentTask: PaidAppointmentTask,
                                     userId: Int,
                                     vehicleId: Int): Future[Either[ServerError, TookanService.AppointmentResponse]] = {

    bookingService.reserveBooking(appointmentTask.dateTime).flatMap {
      case Some(timeSlot) =>
        (for {
          taskData <- EitherT(loadCustomerTaskData(appointmentTask, userId, vehicleId, timeSlot))
          charge <- EitherT(charge(taskData))
          tookanTask <- EitherT(createTookanAppointment(taskData).flatMap(refund(_, charge)))
        } yield (taskData, charge, tookanTask)).inner
          .flatMap {
            case Left(error) =>
              bookingService.releaseBooking(timeSlot)
                .map(_ => Left(error))
            case Right((taskData, charge, tookanTask)) =>
              charge.map(crg => stripeService.updateChargeMetadata(crg, tookanTask.jobId))
              saveTask(taskData, charge, tookanTask)
          }

      case None =>
        Future.successful(Left(ServerError(s"Failed to book time slot for ${appointmentTask.dateTime}")))
    }
  }

  private def loadCustomerTaskData(appointmentTask: PaidAppointmentTask, userId: Int, vehicleId: Int, timeSlot: TimeSlotsRow): Future[Either[ServerError, TaskData]] = {
    (for {
      userWithVehicle <- loadUserWithVehicle(userId, vehicleId)
      serviceInformation <- getServiceInformation(appointmentTask, userWithVehicle._2)
    } yield Right(TaskData(userWithVehicle._1, userWithVehicle._2, timeSlot, serviceInformation))).recover {
      case e: Exception =>
        Logger.error(s"Failed to load user with id '$userId' and vehicle '$vehicleId'", e)
        Left(ServerError(s"User with such vehicle was not found"))
    }
  }

  private def loadUserWithVehicle(userId: Int, vehicleId: Int) = {
    usersService.loadUserWithVehicle(userId, vehicleId).map {
      case (userRow, vehicleRow) =>
        val persistedUser = userRow.toPersistedUser
        val persistedVehicle = vehicleRow.toPersistedVehicle
        (persistedUser, persistedVehicle)
    }
  }

  private def charge(data: TaskData)(implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, Option[Charge]]] = {

    def pay[T <: PaymentInformation](price: Int, paymentInformation: T): Future[Either[ErrorResponse, Charge]] = {
      val description = data.serviceInformation.services.map(_.name).mkString("; ")
      paymentInformation match {
        case customer: CustomerPaymentInformation =>
          def payWithCard = getStripeId(data.user).map {
            id =>
              val paymentSource = StripeService.PaymentSource(id, customer.cardId)
              stripeService.charge(price, paymentSource, description)
          }.getOrElse(Future(Left(ErrorResponse("User doesn't set up account to perform payment", StripeError))))

          customer.token.map(token => stripeService.charge(price, token, description))
            .getOrElse(payWithCard)
        case anonymous: AnonymousPaymentInformation =>
          stripeService.charge(price, anonymous.token, description)
      }
    }

    val basePrice = data.serviceInformation.services.map(_.price).sum
    val price = calculatePrice(basePrice, appointmentTask.promotion)
    price match {
      case x if x > 50 =>
        pay(x, appointmentTask.paymentInformation).map {
          case Left(error) =>
            Left(ServerError(error.message, Some(error.errorType)))
          case Right(charge) =>
            Right(Some(charge))
        }
      case _ =>
        Future.successful(Right(None))
    }
  }

  private def refund(tookanResponse: Either[ServerError, AppointmentResponse], chargeOption: Option[Charge]): Future[Either[ServerError, AppointmentResponse]] = {
    tookanResponse match {
      case Left(error) =>
        chargeOption.map {
          charge =>
            stripeService.refund(charge.getId).map {
              case Left(stripeError) =>
                Logger.warn(s"Failed to refund payment for customer ${charge.getCustomer}. ${stripeError.message}")
                Left(error)
              case Right(refund) =>
                Logger.info(s"Successfully refunded ${refund.getAmount} for customer ${charge.getCustomer}")
                Left(error)
            }
        }.getOrElse(Future.successful(Left(error)))

      case Right(tookanTask) =>
        Future.successful(Right(tookanTask))
    }
  }

  private def createTookanAppointment[T <: AppointmentTask](taskData: TaskData)(implicit appointmentTask: T): Future[Either[ServerError, AppointmentResponse]] = {
    val metadata = getMetadata(taskData.vehicle, taskData.serviceInformation.services)
    val user = taskData.user
    tookanService.createAppointment(user.name, user.phone, appointmentTask.address, appointmentTask.description,
      appointmentTask.dateTime, Option(appointmentTask.latitude), Option(appointmentTask.longitude), user.email, metadata)
      .map(_.left.map(error => ServerError(error.message, Some(TookanError))))
  }

  private def saveTask(data: TaskData,
                       charge: Option[Charge],
                       tookanTask: AppointmentResponse)(implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, AppointmentResponse]] = {
    val paymentMethod = getPaymentMethod(appointmentTask.paymentInformation)
    val basePrice = data.serviceInformation.services.map(_.price).sum
    val insertTask = (for {
      taskId <- (
        Tasks.map(task => (task.jobId, task.userId, task.scheduledTime, task.vehicleId, task.hasInteriorCleaning, task.latitude, task.longitude, task.timeSlotId))
          returning Tasks.map(_.id)
          += ((tookanTask.jobId, data.user.id, Timestamp.valueOf(appointmentTask.dateTime), data.vehicle.id,
          data.serviceInformation.hasInteriorCleaning, appointmentTask.latitude, appointmentTask.longitude, Some(data.timeSlot.id)))
        )
      _ <- (
        PaymentDetails.map(paymentDetails => (paymentDetails.taskId, paymentDetails.paymentMethod, paymentDetails.price,
          paymentDetails.tip, paymentDetails.promotion, paymentDetails.chargeId))
          += ((taskId, paymentMethod, basePrice, 0, appointmentTask.promotion.getOrElse(0), charge.map(_.getId)))
        )
      _ <- DBIO.sequence(
        data.serviceInformation.services
          .map(service => TaskServices.map(taskService => (taskService.price, taskService.name, taskService.taskId)) += ((service.price, service.name, taskId)))
      )
    } yield ()).transactionally
    db.run(insertTask).map {
      _ =>
        refreshTask(tookanTask.jobId)
        Right(tookanTask)
    }
  }
}

object TempTaskService {

  case class TaskData(user: PersistedUser,
                      vehicle: PersistedVehicle,
                      timeSlot: TimeSlotsRow,
                      serviceInformation: ServiceInformation)

}
