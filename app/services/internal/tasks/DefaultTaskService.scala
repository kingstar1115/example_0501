package services.internal.tasks

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import actors.TasksActor
import actors.TasksActor.RefreshTaskData
import akka.actor.ActorSystem
import com.stripe.model.Charge
import commons.ServerError
import commons.enums.TaskStatuses.Successful
import commons.enums.{PaymentMethods, StripeError, TookanError}
import models.Tables._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import services.StripeService.ErrorResponse
import services.TookanService.{AppointmentResponse, Metadata}
import services.internal.notifications.PushNotificationService
import services.internal.services.ServicesService
import services.internal.settings.SettingsService
import services.internal.tasks.DefaultTaskService._
import services.internal.tasks.TasksService.{AbstractUser, AbstractVehicle, AppointmentTask, _}
import services.internal.users.UsersService
import services.{StripeService, TookanService}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class DefaultTaskService @Inject()(tookanService: TookanService,
                                   settingsService: SettingsService,
                                   stripeService: StripeService,
                                   dbConfigProvider: DatabaseConfigProvider,
                                   system: ActorSystem,
                                   pushNotificationService: PushNotificationService,
                                   servicesService: ServicesService,
                                   usersService: UsersService) extends TasksService {

  private val db = dbConfigProvider.get.db

  def pay[T <: PaymentInformation](price: Int, jobId: Long, stripeId: Option[String] = None, paymentInformation: T)(implicit serviceInformation: ServiceInformation): Future[Either[ErrorResponse, Charge]] = {
    val description = serviceInformation.services.map(_.name).mkString("; ")
    paymentInformation match {
      case customer: CustomerPaymentInformation =>
        def payWithCard = stripeId.map { id =>
          val paymentSource = StripeService.PaymentSource(id, customer.cardId)
          stripeService.charge(price, paymentSource, jobId, description)
        }.getOrElse(Future(Left(ErrorResponse("User doesn't set up account to perform payment", StripeError))))

        customer.token.map(token => stripeService.charge(price, token, jobId, description))
          .getOrElse(payWithCard)
      case anonymous: AnonymousPaymentInformation =>
        stripeService.charge(price, anonymous.token, jobId, description)
    }
  }

  def getPaymentMethod(paymentInformation: PaymentInformation): String = {
    paymentInformation match {
      case customer: CustomerPaymentInformation =>
        customer.cardId.getOrElse(PaymentMethods.ApplePay.toString)
    }
  }

  override def createTaskForCustomer(implicit appointmentTask: PaidAppointmentTask, userId: Int, vehicleId: Int): Future[Either[ServerError, AppointmentResponse]] = {

    def mapUserWithVehicle(row: (UsersRow, VehiclesRow)) = (row._1.toPersistedUser, row._2.toPersistedVehicle)

    def saveTask(data: TempData, tookanTask: AppointmentResponse) = {
      val paymentMethod = getPaymentMethod(appointmentTask.paymentInformation)
      val insertTask = (for {
        taskId <- (
          Tasks.map(task => (task.jobId, task.userId, task.scheduledTime, task.vehicleId, task.hasInteriorCleaning, task.latitude, task.longitude))
            returning Tasks.map(_.id)
            += ((tookanTask.jobId, userId, Timestamp.valueOf(appointmentTask.dateTime), vehicleId, data.serviceInformation.hasInteriorCleaning, appointmentTask.latitude, appointmentTask.longitude))
          )
        _ <- (
          PaymentDetails.map(paymentDetails => (paymentDetails.taskId, paymentDetails.paymentMethod, paymentDetails.price, paymentDetails.tip, paymentDetails.promotion, paymentDetails.chargeId))
            += ((taskId, paymentMethod, data.basePrice, 0, appointmentTask.promotion.getOrElse(0), data.chargeId))
          )
        _ <- DBIO.sequence(
          data.serviceInformation.services
            .map(service => TaskServices.map(taskService => (taskService.price, taskService.name, taskService.taskId)) += ((service.price, service.name, taskId)))
        )
      } yield ()).transactionally
      db.run(insertTask).map { _ =>
        refreshTask(tookanTask.jobId)
        Right(tookanTask)
      }
    }

    usersService.loadUserWithVehicle(userId, vehicleId)(mapUserWithVehicle)
      .flatMap(tuple => createPaidTask(tuple._1, tuple._2)(saveTask))
  }

  override def createTaskForAnonymous(implicit paidAppointmentTask: PaidAppointmentTask, user: User, vehicle: Vehicle): Future[Either[ServerError, AppointmentResponse]] = {
    def saveTask(data: TempData, tookanTask: AppointmentResponse) = Future(Right(tookanTask))

    createPaidTask(user, vehicle)(saveTask)
  }

  override def createPartnershipTask(implicit appointmentTask: AppointmentTask, user: User, vehicle: Vehicle): Future[Either[ServerError, AppointmentResponse]] = {
    def onTaskCreated[T <: AppointmentTask](task: T, response: AppointmentResponse) = Future(Right(response))

    getServiceInformation(appointmentTask, vehicle).flatMap { serviceInformation =>
      createTask(user, vehicle, serviceInformation)(onTaskCreated)
    }
  }

  private def createTask[T <: AppointmentTask, V <: AbstractVehicle, U <: AbstractUser](user: U, vehicle: V, serviceInformation: ServiceInformation)
                                                                                       (onTaskCreated: (T, AppointmentResponse) => Future[Either[ServerError, AppointmentResponse]])
                                                                                       (implicit appointmentTask: T): Future[Either[ServerError, AppointmentResponse]] = {
    val metadata = getMetadata(vehicle, serviceInformation.services)
    tookanService.createAppointment(user.name, user.phone, appointmentTask.address, appointmentTask.description,
      appointmentTask.dateTime, Option(appointmentTask.latitude), Option(appointmentTask.longitude), user.email, metadata).flatMap {
      case Left(error) =>
        val tookanError = ServerError(error.message, Option(TookanError))
        Future.successful(Left(tookanError))
      case Right(appointmentResponse) =>
        onTaskCreated(appointmentTask, appointmentResponse)
    }
  }

  private def createPaidTask[V <: AbstractVehicle, U <: AbstractUser](user: U, vehicle: V)
                                                                     (saveTask: (TempData, AppointmentResponse) => Future[Either[ServerError, AppointmentResponse]])
                                                                     (implicit paidAppointmentTask: PaidAppointmentTask): Future[Either[ServerError, AppointmentResponse]] = {
    getServiceInformation(paidAppointmentTask, vehicle).flatMap { implicit serviceInformation =>

      def charge(response: AppointmentResponse, basePrice: Int, price: Int) = {
        Logger.debug(s"Charging $price from user for task ${response.jobId}")
        pay(price, response.jobId, getStripeId(user), paidAppointmentTask.paymentInformation).flatMap {
          case Left(error) =>
            tookanService.deleteTask(response.jobId)
              .map(_ => Left(ServerError(error.message, Option(error.errorType))))
          case Right(charge) =>
            val data = TempData(serviceInformation, basePrice, Option(charge.getId))
            saveTask(data, response);
        }
      }

      def saveFreeTask(response: AppointmentResponse, basePrice: Int) = {
        Logger.debug(s"Task ${response.jobId} is free for user")
        val data = TempData(serviceInformation, basePrice, None)
        saveTask(data, response)
      }

      def onTaskCreated(dto: PaidAppointmentTask, response: AppointmentResponse) = {
        getServiceInformation(dto, vehicle).flatMap { serviceInformation =>
          val basePrice = serviceInformation.services.map(_.price).sum
          val price = calculatePrice(basePrice, dto.promotion)
          price match {
            case x if x > 50 =>
              charge(response, basePrice, price)
            case _ =>
              saveFreeTask(response, basePrice)
          }
        }
      }

      createTask(user, vehicle, serviceInformation)(onTaskCreated)
    }
  }

  private def getStripeId[U <: AbstractUser](user: U): Option[String] = {
    user match {
      case persistedUser: PersistedUser => persistedUser.stipeId
      case _ => None
    }
  }

  private def getServiceInformation[V <: AbstractVehicle](appointmentTask: AppointmentTask, vehicle: V): Future[ServiceInformation] = {
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

  private def calculatePrice(priceBeforeDiscount: Int, discount: Option[Int] = None): Int = {
    discount.map { discountAmount =>
      Logger.debug(s"Washing price: $priceBeforeDiscount. Discount: $discountAmount")
      val discountedPrice = priceBeforeDiscount - discountAmount
      if (discountedPrice > 0 && discountedPrice < 50) 0 else discountedPrice
    }.getOrElse(priceBeforeDiscount)
  }

  override def refreshTask(taskId: Long): Unit = {
    system.actorOf(TasksActor.props(tookanService, dbConfigProvider, pushNotificationService)) ! RefreshTaskData(taskId)
  }

  override def pendingTasks(userId: Int): Future[Seq[(TasksRow, Option[AgentsRow], VehiclesRow)]] = {
    val taskQuery = for {
      ((task, agent), vehicle) <- Tasks joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if task.jobStatus === Successful.code && task.submitted === false && task.userId === userId
    } yield (task, agent, vehicle)
    db.run(taskQuery.result)
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

  case class TempData(serviceInformation: ServiceInformation, basePrice: Int, chargeId: Option[String])

}