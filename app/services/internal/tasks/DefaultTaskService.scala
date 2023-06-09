package services.internal.tasks

import java.sql.Timestamp
import java.time.LocalDateTime

import actors.TasksActor.RefreshTaskData
import akka.actor.ActorRef
import akka.pattern.ask
import com.stripe.model.Charge
import commons.ServerError
import commons.enums.TaskStatuses.Successful
import commons.enums.{PaymentMethods, StripeError, TookanError}
import commons.monads.transformers.EitherT
import controllers.rest.TasksController
import controllers.rest.TasksController.{CompleteTaskDto, TaskDetailsDto}
import javax.inject.{Inject, Named}
import models.Tables._
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits._
import services.StripeService.{CustomerCardCharge, ErrorResponse, TokenCharge}
import services.TookanService.{AppointmentDetails, AppointmentResponse, Metadata}
import services.internal.bookings.BookingService
import services.internal.services.ServicesService
import services.internal.tasks.DefaultTaskService._
import services.internal.tasks.TasksService._
import services.internal.users.UsersService
import services.{StripeService, TookanService}
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future
import scala.concurrent.duration._

class DefaultTaskService @Inject()(tookanService: TookanService,
                                   stripeService: StripeService,
                                   val dbConfigProvider: DatabaseConfigProvider,
                                   @Named("taskActor") tasksActor: ActorRef,
                                   servicesService: ServicesService,
                                   usersService: UsersService,
                                   bookingService: BookingService)
  extends TasksService with HasDatabaseConfigProvider[JdbcProfile] {

  private val logger = Logger(this.getClass)

  override def createTaskForCustomer(userId: Int, vehicleId: Int)
                                    (implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, TookanService.AppointmentResponse]] = {

    reserveBooking(appointmentTask) { (timeSlot, daySlot) =>
      (for {
        taskData <- EitherT(loadCustomerTaskData(userId, vehicleId, timeSlot))
        charge <- EitherT(charge(taskData))
        tookanTask <- EitherT(createTookanAppointment(taskData, timeSlot, daySlot).flatMap(refund(_, charge)))
      } yield (taskData, charge, tookanTask)).inner
        .flatMap {
          case Left(error) =>
            bookingService.releaseBooking(timeSlot)
              .map(_ => Left(error))
          case Right((taskData, charge, tookanTask)) =>
            charge.map(c => stripeService.updateChargeMetadata(c, tookanTask.jobId))
            saveTask(taskData, charge, tookanTask, timeSlot, daySlot)
        }
    }
  }

  private def loadCustomerTaskData(userId: Int, vehicleId: Int, timeSlot: TimeSlotsRow)
                                  (implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, TaskData[PersistedUser, PersistedVehicle]]] = {
    (for {
      (user, vehicle) <- loadUserWithVehicle(userId, vehicleId)
      serviceInformation <- getServiceInformation(appointmentTask, vehicle)
    } yield Right(TaskData(user, vehicle, timeSlot, serviceInformation))).recover {
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
    reserveBooking(appointmentTask) { (timeSlot, daySlot) =>
      getServiceInformation(appointmentTask, vehicle).flatMap { serviceInformation =>
        val taskData = TaskData(user, vehicle, timeSlot, serviceInformation)
        (for {
          charge <- EitherT(charge(taskData))
          tookanTask <- EitherT(createTookanAppointment(taskData, timeSlot, daySlot).flatMap(refund(_, charge)))
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
    reserveBooking(appointmentTask) { (timeSlot, daySlot) =>
      getServiceInformation(appointmentTask, vehicle).flatMap { serviceInformation =>
        val taskData = TaskData(user, vehicle, timeSlot, serviceInformation)
        createTookanAppointment(taskData, timeSlot, daySlot)
      }
    }
  }

  private def reserveBooking[T](appointmentTask: AppointmentTask)(mapper: (TimeSlotsRow, DaySlotsRow) => Future[Either[ServerError, T]]): Future[Either[ServerError, T]] = {
    (appointmentTask match {
      case appointmentWithTimeSlot: ZonedTimeSlot =>
        bookingService.reserveBooking(appointmentWithTimeSlot.timeSlot)
      case _ =>
        bookingService.reserveBooking(appointmentTask.dateTime)
    }).flatMap {
      case Some((timeSlot, daySlot)) =>
        mapper.apply(timeSlot, daySlot)
      case None =>
        Future.successful(Left(ServerError("Oops! We are sorry, but this time is no longer available. Please select another one.")))
    }
  }

  private def charge[U <: AbstractUser, V <: AbstractVehicle]
  (data: TaskData[U, V])(implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, Option[Charge]]] = {

    def pay[T <: PaymentInformation](price: Int, paymentInformation: T) = {
      val description = data.serviceInformation.services.map(_.name).mkString("; ")
      paymentInformation match {
        case customer: CustomerPaymentInformation =>
          customer.token.map(token => {
            val chargeRequest = TokenCharge(data.user.email, token, price, description)
            stripeService.charge(chargeRequest)
          }).getOrElse {
            getStripeId(data.user) match {
              case Some(id) =>
                val chargeRequest = CustomerCardCharge(id, customer.cardId, price, description)
                stripeService.charge(chargeRequest)

              case _ =>
                Future(Left(ErrorResponse("User doesn't set up account to perform payment", StripeError)))
            }
          }
        case anonymous: AnonymousPaymentInformation =>
          val chargeRequest = TokenCharge(data.user.email, anonymous.token, price, description)
          stripeService.charge(chargeRequest)
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
    val priceWithAppliedPromotion = appointmentTask.promotion.map { promotion =>
      Logger.info(s"Promotion value: `$promotion`")
      price - adjustPromotionValue(promotion)
    }.getOrElse(price)

    val priceWithAppliedDiscount = appointmentTask.discount.map{ discount =>
      Logger.info(s"Discount value: `$discount%`")
      val result: Int = priceWithAppliedPromotion - ((priceWithAppliedPromotion * discount) / 100)
      Logger.info(s"Discounted value: `$result`")
      result
    }.getOrElse(priceWithAppliedPromotion)

    val priceWithTip = appointmentTask match {
      case x: PaidAnonymousAppointmentTask =>
        x.tip.map { tip =>
          Logger.debug(s"Adding tip: $tip")
          priceWithAppliedDiscount + tip
        }.getOrElse(priceWithAppliedDiscount)
      case _ =>
        priceWithAppliedDiscount
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
  (taskData: TaskData[U, V], timeSlot: TimeSlotsRow, daySlot: DaySlotsRow)
  (implicit appointmentTask: T): Future[Either[ServerError, AppointmentResponse]] = {
    val metadata = getMetadata(taskData.vehicle, taskData.serviceInformation.services)
    val user = taskData.user
    tookanService.createAppointment(user.name, user.phone, appointmentTask.address, appointmentTask.description,
      LocalDateTime.of(daySlot.date.toLocalDate, timeSlot.startTime.toLocalTime), Option(appointmentTask.latitude),
      Option(appointmentTask.longitude), user.email, metadata)
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
                       tookanTask: AppointmentResponse,
                       timeSlot: TimeSlotsRow,
                       daySlot: DaySlotsRow)
                      (implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, AppointmentResponse]] = {
    val paymentMethod = getPaymentMethod(appointmentTask.paymentInformation)
    val basePrice = data.serviceInformation.services.map(_.price).sum
    val promotion = appointmentTask.promotion
      .map(promotion => {
        if (promotion > 0 && promotion < 100)
          promotion * 100
        else
          promotion
      })
      .getOrElse(0)

    val scheduledTime = Timestamp.valueOf(LocalDateTime.of(daySlot.date.toLocalDate, timeSlot.startTime.toLocalTime))
    val insertTaskAction = for {
      taskId <- (
        Tasks.map(task => (task.jobId, task.userId, task.scheduledTime, task.vehicleId, task.hasInteriorCleaning, task.latitude, task.longitude, task.timeSlotId))
          returning Tasks.map(_.id)
          += ((tookanTask.jobId, data.user.id, scheduledTime, data.vehicle.id,
          data.serviceInformation.hasInteriorCleaning, appointmentTask.latitude, appointmentTask.longitude, data.timeSlot.id))
        )
      _ <- (
        PaymentDetails.map(paymentDetails => (paymentDetails.taskId, paymentDetails.paymentMethod, paymentDetails.price,
          paymentDetails.tip, paymentDetails.promotion, paymentDetails.chargeId))
          += ((taskId, paymentMethod, basePrice, 0, promotion, charge.map(_.getId)))
        )
      _ <- DBIO.sequence(
        data.serviceInformation.services
          .map(service => TaskServices.map(taskService => (taskService.price, taskService.name, taskService.taskId)) += ((service.price, service.name, taskId)))
      )
    } yield ()
    val insertAction = ((data.user.stripeId, charge) match {
      case (None, Some(taskCharge)) =>
        insertTaskAction.zip(Users.filter(_.id === data.user.id).map(_.stripeId).update(Option(taskCharge.getCustomer)))
      case _ =>
        insertTaskAction
    }).transactionally

    db.run(insertAction).map {
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
      case PersistedUser(_, _, _, _, Some(stripeId)) => Some(stripeId)
      case PersistedUser(_, _, _, Some(email), None) => Some(stripeService.createCustomerIfNotExists(email).getId)
      case _ => None
    }
  }

  def getServiceInformation[V <: AbstractVehicle](appointmentTask: AppointmentTask, vehicle: V): Future[ServiceInformation] = {
    def getServicePrice(service: ServicesRow) = {
      vehicle match {
        case persistedVehicle: PersistedVehicle =>
          servicesService.getServicePrice(service, persistedVehicle.id, persistedVehicle.userId)
        case _ =>
          servicesService.getServicePrice(service, vehicle.maker, vehicle.model, vehicle.year)
      }
    }

    //TODO: improve error handling
    appointmentTask match {
      case dto: ServicesInformation =>
        val servicesFuture = for {
          serviceWithExtras <- servicesService.getServiceWithExtras(dto.serviceId, dto.extras)
          servicePrice <- getServicePrice(serviceWithExtras.service)
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
          service <- serviceFuture
          servicePrice <- getServicePrice(service)
        } yield (service, servicePrice)).map { tuple =>
          ServiceInformation(Seq(Service(tuple._1.name, tuple._2)), dto.hasInteriorCleaning)
        }
    }
  }

  override def refreshTask(taskId: Long): Future[Either[String, AppointmentDetails]] = {
    tasksActor.?(RefreshTaskData(taskId))(30.seconds)
      .mapTo[Either[String, AppointmentDetails]]
  }

  override def pendingTasks(userId: Int): Future[Seq[(TasksRow, Option[AgentsRow], VehiclesRow)]] = {
    val taskQuery = for {
      ((task, agent), vehicle) <- Tasks joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if task.jobStatus === Successful.code && task.submitted === false && task.userId === userId
    } yield (task, agent, vehicle)
    db.run(taskQuery.result)
  }

  override def completeTask(dto: TasksController.CompleteTaskDto, userId: Int): Future[Either[ServerError, TasksRow]] = {
    (for {
      taskWithUser <- EitherT(loadTaskForCompleting(dto.jobId, userId))
      charge <- EitherT(chargeTip(dto, taskWithUser._2))
    } yield (taskWithUser._1, charge)).inner.flatMap {
      case Right((task, chargeOptional)) =>
        (for {
          jobHash <- task.jobHash
          customerReview <- dto.customerReview
        } yield TookanService.CustomerReview(customerReview.rating, customerReview.comment, jobHash))
          .map(tookanService.leaveCustomerReview)

        val updated = task.copy(submitted = true, rating = dto.customerReview.map(_.rating))
        val updateAction = chargeOptional.map { charge =>
          DBIO.seq(
            //TODO: Migrate to long
            PaymentDetails.filter(_.taskId === updated.id).map(_.tip).update(charge.getAmount.toInt),
            Tasks.update(updated)
          ).transactionally
        }.getOrElse(Tasks.update(updated))
        db.run(updateAction).map(_ => Right(updated))

      case Left(error) =>
        Future.successful(Left(error))
    }
  }

  private def loadTaskForCompleting(jobId: Long, userId: Int): Future[Either[ServerError, (TasksRow, UsersRow)]] = {
    val taskWithUser = for {
      (task, user) <- Tasks join Users on (_.userId === _.id)
      if task.jobId === jobId && task.jobStatus === Successful.code && task.submitted === false && task.userId === userId
    } yield (task, user)
    db.run(taskWithUser.result.headOption).map {
      case None =>
        Logger.warn(s"Task $jobId for user $userId was not found for completing")
        Left(ServerError("Failed to complete task"))
      case Some((task, user)) =>
        Right((task, user))
    }
  }

  private def chargeTip(completeTaskDto: CompleteTaskDto, user: UsersRow): Future[Either[ServerError, Option[Charge]]] = {
    stripeService.createTipCharge(user, completeTaskDto.tip)
      .map(chargeRequest => {
        logger.info(s"Charging tip for task ${completeTaskDto.jobId} from $chargeRequest")
        val metadata = Map(("jobId", completeTaskDto.jobId.toString))
        stripeService.charge(chargeRequest, metadata).map {
          case Right(charge) =>
            Right(Some(charge))
          case Left(error) =>
            logger.warn(s"Failed to charge tip for $chargeRequest for task ${completeTaskDto.jobId}. Error: ${error.message}")
            Left(ServerError(error.message, Some(error.errorType)))
        }
      }).getOrElse(Future.successful(Right(None)))
  }

  override def getTask(id: Long, userId: Int): Future[Option[TaskDetailsDto]] = {
    val selectQuery = for {
      ((((task, agent), vehicle), paymentDetails), services) <- Tasks
        .joinLeft(Agents).on(_.agentId === _.id)
        .join(Vehicles).on(_._1.vehicleId === _.id)
        .join(PaymentDetails).on(_._1._1.id === _.taskId)
        .join(TaskServices).on(_._1._1._1.id === _.taskId)
      if task.userId === userId && task.jobId === id
    } yield (task, agent, vehicle, paymentDetails, services)
    db.run(selectQuery.result).map(result => {
      if (result.isEmpty) {
        None
      } else {
        Some(TaskDetailsDto.convert(result.head, result.map(_._5)))
      }
    })
  }

}

object DefaultTaskService {

  implicit class ExtUsersRow(user: UsersRow) {
    def toPersistedUser = PersistedUser(user.id, String.format("%s %s", user.firstName, user.lastName),
      user.phoneCode.concat(user.phone), Option(user.email), user.stripeId)
  }

  implicit class ExtVehiclesRow(vehicle: VehiclesRow) {
    def toPersistedVehicle = PersistedVehicle(vehicle.makerNiceName, vehicle.modelNiceName, vehicle.year,
      vehicle.color, vehicle.licPlate, vehicle.id, vehicle.userId)
  }

  case class Service(name: String, price: Int)

  case class ServiceInformation(services: Seq[Service], hasInteriorCleaning: Boolean)

  case class TaskData[U <: AbstractUser, V <: AbstractVehicle](user: U,
                                                               vehicle: V,
                                                               timeSlot: TimeSlotsRow,
                                                               serviceInformation: ServiceInformation)

}