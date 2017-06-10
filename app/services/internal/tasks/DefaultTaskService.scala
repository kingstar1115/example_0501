package services.internal.tasks

import java.sql.Timestamp
import java.time.LocalDateTime
import javax.inject.Inject

import actors.TasksActor
import actors.TasksActor.RefreshTaskData
import akka.actor.ActorSystem
import com.stripe.model.Charge
import commons.ServerError
import commons.enums.TaskStatuses.Successful
import commons.enums.{PaymentMethods, StripeError, TookanError}
import commons.monads.transformers.EitherT
import models.Tables._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.concurrent.Execution.Implicits._
import services.StripeService.ErrorResponse
import services.TookanService.{AppointmentResponse, Metadata}
import services.internal.bookings.BookingService
import services.internal.notifications.PushNotificationService
import services.internal.services.ServicesService
import services.internal.settings.SettingsService
import services.internal.tasks.DefaultTaskService._
import services.internal.tasks.TasksService._
import services.internal.users.UsersService
import services.{StripeService, TookanService}
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future

class DefaultTaskService @Inject()(tookanService: TookanService,
                                   settingsService: SettingsService,
                                   stripeService: StripeService,
                                   dbConfigProvider: DatabaseConfigProvider,
                                   system: ActorSystem,
                                   pushNotificationService: PushNotificationService,
                                   servicesService: ServicesService,
                                   usersService: UsersService,
                                   bookingService: BookingService) extends TasksService {

  private val db = dbConfigProvider.get.db

  override def createTaskForCustomer(userId: Int, vehicleId: Int)
                                    (implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, TookanService.AppointmentResponse]] = {

    reserveBooking(appointmentTask.dateTime) { timeSlot =>
      (for {
        taskData <- EitherT(loadCustomerTaskData(userId, vehicleId, timeSlot))
        charge <- EitherT(charge(taskData))
        tookanTask <- EitherT(createTookanAppointment(taskData).flatMap(refund(_, charge)))
      } yield (taskData, charge, tookanTask)).inner
        .flatMap {
          case Left(error) =>
            bookingService.releaseBooking(timeSlot)
              .map(_ => Left(error))
          case Right((taskData, charge, tookanTask)) =>
            charge.map(c => stripeService.updateChargeMetadata(c, tookanTask.jobId))
            saveTask(taskData, charge, tookanTask)
        }
    }
  }

  private def loadCustomerTaskData(userId: Int, vehicleId: Int, timeSlot: TimeSlotsRow)
                                  (implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, TaskData[PersistedUser, PersistedVehicle]]] = {
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

  override def createTaskForAnonymous(user: User, vehicle: Vehicle)
                                     (implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, AppointmentResponse]] = {
    reserveBooking(appointmentTask.dateTime) { timeSlot =>
      getServiceInformation(appointmentTask, vehicle).flatMap { serviceInformation =>
        val taskData = TaskData(user, vehicle, timeSlot, serviceInformation)
        (for {
          charge <- EitherT(charge(taskData))
          tookanTask <- EitherT(createTookanAppointment(taskData).flatMap(refund(_, charge)))
        } yield (charge, tookanTask)).inner
          .flatMap {
            case Left(error) =>
              bookingService.releaseBooking(timeSlot)
                .map(_ => Left(error))
            case Right((charge, tookanTask)) =>
              charge.map(c => stripeService.updateChargeMetadata(c, tookanTask.jobId))
              Future(Right(tookanTask))
          }
      }
    }
  }


  override def createPartnershipTask(user: User, vehicle: Vehicle)
                                    (implicit appointmentTask: AppointmentTask): Future[Either[ServerError, AppointmentResponse]] = {
    reserveBooking(appointmentTask.dateTime) { timeSlot =>
      getServiceInformation(appointmentTask, vehicle).flatMap { serviceInformation =>
        val taskData = TaskData(user, vehicle, timeSlot, serviceInformation)
        createTookanAppointment(taskData)
      }
    }
  }

  private def reserveBooking[T](dateTime: LocalDateTime)(mapper: TimeSlotsRow => Future[Either[ServerError, T]]): Future[Either[ServerError, T]] = {
    bookingService.reserveBooking(dateTime).flatMap {
      case Some(timeSlot) =>
        mapper.apply(timeSlot)
      case None =>
        Logger.info(s"Failed to book time slot for $dateTime")
        Future.successful(Left(ServerError("Oops! We are sorry, but this time is no longer available. Please select another one.")))
    }
  }

  private def charge[U <: AbstractUser, V <: AbstractVehicle](data: TaskData[U, V])
                                                             (implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, Option[Charge]]] = {

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

    val servicesPrice = data.serviceInformation.services.map(_.price).sum
    val price = calculatePrice(servicesPrice)
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

  private def calculatePrice(price: Int)(implicit appointmentTask: PaidAppointmentTask): Int = {

    def adjustPromotionValue(promotion: Int): Int = {
      if (promotion > 0 && promotion < 100) {
        val adjustedPromotion = promotion * 100
        Logger.debug(s"Increased promotion value: $adjustedPromotion")
        adjustedPromotion
      } else {
        promotion
      }
    }

    Logger.debug(s"Calculating task price. Base price: $price")
    val discountedPrice = appointmentTask.promotion.map { promotion =>
      Logger.info(s"Promotion value: $promotion")
      price - adjustPromotionValue(promotion)
    }.getOrElse(price)

    val priceWithTip = appointmentTask match {
      case x: PaidAnonymousAppointmentTask =>
        x.tip.map { tip =>
          Logger.debug(s"Adding tip: $tip")
          discountedPrice + tip
        }.getOrElse(discountedPrice)
      case _ =>
        discountedPrice
    }
    Logger.debug(s"Calculated task price: $priceWithTip")
    priceWithTip
  }

  private def refund(tookanResponse: Either[ServerError, AppointmentResponse],
                     chargeOption: Option[Charge]): Future[Either[ServerError, AppointmentResponse]] = {
    tookanResponse match {
      case Left(error) =>
        chargeOption.map { charge =>
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

  private def createTookanAppointment[T <: AppointmentTask, U <: AbstractUser, V <: AbstractVehicle]
  (taskData: TaskData[U, V])
  (implicit appointmentTask: T): Future[Either[ServerError, AppointmentResponse]] = {
    val metadata = getMetadata(taskData.vehicle, taskData.serviceInformation.services)
    val user = taskData.user
    tookanService.createAppointment(user.name, user.phone, appointmentTask.address, appointmentTask.description,
      appointmentTask.dateTime, Option(appointmentTask.latitude), Option(appointmentTask.longitude), user.email, metadata)
      .map(_.left.map(error => ServerError(error.message, Some(TookanError))))
  }

  private def getMetadata[V <: AbstractVehicle](vehicle: V, services: Seq[Service]): Seq[Metadata] = {
    val metadata: Seq[Metadata] = Seq(
      Metadata(Metadata.maker, vehicle.maker),
      Metadata(Metadata.model, vehicle.model),
      Metadata(Metadata.year, vehicle.year.toString),
      Metadata(Metadata.color, vehicle.color),
      Metadata(Metadata.services, services.map(_.name).mkString("; "))
    )
    vehicle.licPlate.map(plateNumber => metadata :+ Metadata(Metadata.plate, plateNumber))
      .getOrElse(metadata)
  }

  private def saveTask(data: TaskData[PersistedUser, PersistedVehicle],
                       charge: Option[Charge],
                       tookanTask: AppointmentResponse)
                      (implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, AppointmentResponse]] = {
    val paymentMethod = getPaymentMethod(appointmentTask.paymentInformation)
    val basePrice = data.serviceInformation.services.map(_.price).sum
    val insertTask = (for {
      taskId <- (
        Tasks.map(task => (task.jobId, task.userId, task.scheduledTime, task.vehicleId, task.hasInteriorCleaning, task.latitude, task.longitude, task.timeSlotId))
          returning Tasks.map(_.id)
          += ((tookanTask.jobId, data.user.id, Timestamp.valueOf(appointmentTask.dateTime), data.vehicle.id,
          data.serviceInformation.hasInteriorCleaning, appointmentTask.latitude, appointmentTask.longitude, data.timeSlot.id))
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

  def getPaymentMethod(paymentInformation: PaymentInformation): String = {
    paymentInformation match {
      case customer: CustomerPaymentInformation =>
        customer.cardId.getOrElse(PaymentMethods.ApplePay.toString)
    }
  }

  def getStripeId[U <: AbstractUser](user: U): Option[String] = {
    user match {
      case persistedUser: PersistedUser => persistedUser.stripeId
      case _ => None
    }
  }

  def getServiceInformation[V <: AbstractVehicle](appointmentTask: AppointmentTask, vehicle: V): Future[ServiceInformation] = {
    //TODO: improve error handling
    appointmentTask match {
      case dto: ServicesInformation =>
        val servicesFuture = for {
          serviceWithExtras <- servicesService.getServiceWithExtras(dto.serviceId, dto.extras)
          servicePrice <- servicesService.getServicePrice(serviceWithExtras.service, vehicle.maker, vehicle.model, vehicle.year)
        } yield (serviceWithExtras, servicePrice)

        servicesFuture.map { tuple =>
          val serviceRow = tuple._1.service
          val extrasTuple = tuple._1.extras.map(extra => (extra.name, extra.price))
          val services = (Seq((serviceRow.name, tuple._2)) ++ extrasTuple) map Service.tupled
          ServiceInformation(services, servicesService.hasInteriorCleaning(serviceRow))
        }

      case dto: InteriorCleaning =>
        val serviceFuture = if (dto.hasInteriorCleaning)
          servicesService.getExteriorAndInteriorCleaningService
        else
          servicesService.getExteriorCleaningService

        (for {
          serviceRow <- serviceFuture
          servicePrice <- servicesService.getServicePrice(serviceRow, vehicle.maker, vehicle.model, vehicle.year)
        } yield (serviceRow, servicePrice)).map { tuple =>
          val service = Service(tuple._1.name, tuple._2)
          ServiceInformation(Seq(service), dto.hasInteriorCleaning)
        }
    }
  }

  override def refreshTask(taskId: Long): Unit = {
    system.actorOf(TasksActor.props(tookanService, dbConfigProvider, pushNotificationService, bookingService)) ! RefreshTaskData(taskId)
  }

  override def pendingTasks(userId: Int): Future[Seq[(TasksRow, Option[AgentsRow], VehiclesRow)]] = {
    val taskQuery = for {
      ((task, agent), vehicle) <- Tasks joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if task.jobStatus === Successful.code && task.submitted === false && task.userId === userId
    } yield (task, agent, vehicle)
    db.run(taskQuery.result)
  }
}

object DefaultTaskService {

  implicit class ExtUsersRow(user: UsersRow) {
    def toPersistedUser = PersistedUser(user.id, String.format("%s %s", user.firstName, user.lastName),
      user.phoneCode.concat(user.phone), Option(user.email), user.stripeId)
  }

  implicit class ExtVehiclesRow(vehicle: VehiclesRow) {
    def toPersistedVehicle = PersistedVehicle(vehicle.makerNiceName, vehicle.modelNiceName, vehicle.year,
      vehicle.color, vehicle.licPlate, vehicle.id)
  }

  case class Service(name: String, price: Int)

  case class ServiceInformation(services: Seq[Service], hasInteriorCleaning: Boolean)

  case class TaskData[U <: AbstractUser, V <: AbstractVehicle](user: U,
                                                               vehicle: V,
                                                               timeSlot: TimeSlotsRow,
                                                               serviceInformation: ServiceInformation)

}