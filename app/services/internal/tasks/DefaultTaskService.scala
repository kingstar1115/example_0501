package services.internal.tasks

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import actors.TasksActor
import actors.TasksActor.RefreshTaskData
import akka.actor.ActorSystem
import com.stripe.model.Charge
import commons.ServerError
import commons.enums.TaskStatuses.Successful
import commons.enums.{PaymentMethods, TookanError}
import dao.accommodations.AccommodationsDao
import models.Tables._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import services.StripeService.ErrorResponse
import services.TookanService.{AppointmentResponse, Metadata}
import services.internal.accommodations.AccommodationsService
import services.internal.notifications.PushNotificationService
import services.internal.settings.SettingsService
import services.internal.tasks.DefaultTaskService._
import services.internal.tasks.TasksService.{AppointmentTask, _}
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
                                   accommodationDao: AccommodationsDao,
                                   accommodationsService: AccommodationsService,
                                   usersService: UsersService) extends TasksService {

  val db = dbConfigProvider.get.db

  def pay[T <: PaymentInformation](price: Int, jobId: Long, paymentInformation: T, stripeId: Option[String] = None): Future[Either[ErrorResponse, Charge]] = {
    paymentInformation match {
      case customer: CustomerPaymentInformation =>
        customer.token.map(token => stripeService.charge(price, token, jobId))
          .getOrElse {
            //TODO: StripeId validation
            val paymentSource = StripeService.PaymentSource(stripeId.get, customer.cardId)
            stripeService.charge(price, paymentSource, jobId)
          }
      case anonymous: AnonymousPaymentInformation =>
        stripeService.charge(price, anonymous.token, jobId)
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
      data.serviceInformation.services.map(service => TaskServices.map(taskService => (taskService.price, taskService.name, taskService.jobId)) += ((service.price, service.name, 1)))
      val insertTask = (for {
        taskId <- (
          Jobs.map(job => (job.jobId, job.userId, job.scheduledTime, job.vehicleId, job.hasInteriorCleaning, job.latitude, job.longitude))
            returning Jobs.map(_.id)
            += ((tookanTask.jobId, userId, Timestamp.valueOf(appointmentTask.dateTime), vehicleId, data.serviceInformation.hasInteriorCleaning, appointmentTask.latitude, appointmentTask.longitude))
          )
        _ <- (
          PaymentDetails.map(paymentDetails => (paymentDetails.jobId, paymentDetails.paymentMethod, paymentDetails.price, paymentDetails.tip, paymentDetails.promotion, paymentDetails.chargeId))
            += ((taskId, paymentMethod, data.basePrice, 0, appointmentTask.promotion.getOrElse(0), data.chargeId))
          )
        _ <- DBIO.sequence(
          data.serviceInformation.services
            .map(service => TaskServices.map(taskService => (taskService.price, taskService.name, taskService.jobId)) += ((service.price, service.name, taskId)))
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

  override def createTaskForAnonymous(implicit appointmentTask: PaidAppointmentTask, user: User, vehicle: Vehicle): Future[Either[ServerError, AppointmentResponse]] = {
    def saveTask(data: TempData, tookanTask: AppointmentResponse) = Future(Right(tookanTask))

    createPaidTask(user, vehicle)(saveTask)
  }

  override def createPartnershipTask(implicit appointmentTask: AppointmentTask, user: User, vehicle: Vehicle): Future[Either[ServerError, AppointmentResponse]] = {
    def onTaskCreated[T <: AppointmentTask](task: T, response: AppointmentResponse) = Future(Right(response))

    createTask(user, vehicle)(onTaskCreated)
  }

  private def createTask[T <: AppointmentTask, V <: AbstractVehicle, U <: AbstractUser](user: U, vehicle: V)
                                                                                       (onTaskCreated: (T, AppointmentResponse) => Future[Either[ServerError, AppointmentResponse]])
                                                                                       (implicit dto: T): Future[Either[ServerError, AppointmentResponse]] = {
    val metadata = getVehicleMetadata(vehicle)
    tookanService.createAppointment(user.name, user.phone, dto.address, dto.description,
      dto.dateTime, Option(dto.latitude), Option(dto.longitude), user.email, metadata).flatMap {
      case Left(error) =>
        val tookanError = ServerError(error.message, Option(TookanError))
        Future.successful(Left(tookanError))
      case Right(task) =>
        onTaskCreated(dto, task)
    }
  }

  private def createPaidTask[V <: AbstractVehicle, U <: AbstractUser](user: U, vehicle: V)
                                                                     (saveTask: (TempData, AppointmentResponse) => Future[Either[ServerError, AppointmentResponse]])
                                                                     (implicit dto: PaidAppointmentTask): Future[Either[ServerError, AppointmentResponse]] = {
    def onTaskCreated(dto: PaidAppointmentTask, tookanTask: AppointmentResponse) = {
      getPrices(dto).flatMap { serviceInformation =>
        val basePrice = serviceInformation.services.map(_.price).sum
        val price = calculatePrice(basePrice, dto.promotion)
        price match {
          case x if x > 50 =>
            Logger.debug(s"Charging $price from user for task ${tookanTask.jobId}")
            val stripeId = user match {
              case u: PersistedUser => u.stipeId
              case _ => None
            }
            pay(price, tookanTask.jobId, dto.paymentInformation, stripeId).flatMap {
              case Left(error) =>
                tookanService.deleteTask(tookanTask.jobId)
                  .map(_ => Left(ServerError(error.message, Option(error.errorType))))
              case Right(charge) =>
                val data = TempData(serviceInformation, basePrice, Option(charge.getId))
                saveTask(data, tookanTask);
            }
          case _ =>
            Logger.debug(s"Task ${tookanTask.jobId} is free for user")
            val data = TempData(serviceInformation, basePrice, None)
            saveTask(data, tookanTask);
        }
      }
    }

    createTask(user, vehicle)(onTaskCreated)
  }

  private def getPrices(dto: AppointmentTask): Future[ServiceInformation] = {
    dto match {
      case dto: Accommodation =>
        db.run(accommodationDao.findByIdWithExtras(dto.accommodation, dto.extras).result).map { resultSet =>
          val accommodation = resultSet.head._1
          val extras: Seq[(String, Int)] = resultSet.filter(_._2.isDefined)
            .map(_._2.get)
            .map(extra => (extra.name, extra.price))
          val services = (Seq((accommodation.name, accommodation.price)) ++ extras) map Service.tupled
          ServiceInformation(services, accommodationsService.hasInteriorCleaning(accommodation))
        }
      case dto: InteriorCleaning =>
        val accommodations = if (dto.hasInteriorCleaning)
          db.run(accommodationDao.getExteriorAndInteriorCleaning
            .map(accommodation => (accommodation.name, accommodation.price)).result)
        else
          db.run(accommodationDao.getExteriorCleaning
            .map(accommodation => (accommodation.name, accommodation.price)).result)

        accommodations.map(_.map(Service.tupled))
          .map(services => ServiceInformation(services, dto.hasInteriorCleaning))
    }
  }

  private def calculatePrice(priceBeforeDiscount: Int, discount: Option[Int] = None): Int = {
    discount.map { discountAmount =>
      Logger.debug(s"Washing price: $priceBeforeDiscount. Discount: $discountAmount")
      val discountedPrice = priceBeforeDiscount - discountAmount
      if (discountedPrice > 0 && discountedPrice < 50) 0 else discountedPrice
    }.getOrElse(priceBeforeDiscount)
  }

  @Deprecated
  def createTask(dto: Int, userId: Int): Future[Either[ServerError, AppointmentResponse]] = {
    throw new RuntimeException("Not supported")
    //    def processPayment(tookanTask: AppointmentResponse, user: UsersRow) = {
    //      def saveTask(price: Int, chargeId: Option[String] = None) = {
    //        val insertTask = (for {
    //          taskId <- (
    //            Jobs.map(job => (job.jobId, job.userId, job.scheduledTime, job.vehicleId, job.hasInteriorCleaning, job.latitude, job.longitude))
    //              returning Jobs.map(_.id)
    //              += ((tookanTask.jobId, userId, Timestamp.valueOf(dto.pickupDateTime), dto.vehicleId, dto.hasInteriorCleaning, dto.pickupLatitude, dto.pickupLongitude))
    //            )
    //          _ <- (
    //            PaymentDetails.map(paymentDetails => (paymentDetails.jobId, paymentDetails.paymentMethod, paymentDetails.price, paymentDetails.tip, paymentDetails.promotion, paymentDetails.chargeId))
    //              += ((taskId, dto.cardId.getOrElse(PaymentMethods.ApplePay.toString), price, 0, dto.promotion.getOrElse(0), chargeId))
    //            )
    //        } yield ()).transactionally
    //        db.run(insertTask).map { _ =>
    //          refreshTask(tookanTask.jobId)
    //          Right(tookanTask)
    //        }
    //      }
    //
    //      def pay(price: Int) = {
    //        if (dto.cardId.isDefined || dto.token.isDefined) {
    //          dto.token.map(token => Option(stripeService.charge(price, token, tookanTask.jobId)))
    //            .getOrElse {
    //              user.stripeId.map { stripeId =>
    //                val paymentSource = StripeService.PaymentSource(stripeId, dto.cardId)
    //                stripeService.charge(price, paymentSource, tookanTask.jobId)
    //              }
    //            }
    //        } else {
    //          Logger.debug(s"Token or Card Id not provided. No charge for task: ${tookanTask.jobId}. Expected charge amount: $price")
    //          Option(Future(Right(new Charge)))
    //        }
    //      }
    //
    //      getPrices(dto).map(_.head).flatMap { accommodation =>
    //        val basePrice = accommodation._2
    //        val price = calculatePrice(basePrice, dto.promotion)
    //        price match {
    //          case x if x > 50 =>
    //            Logger.debug(s"Charging $price from user $userId for task ${tookanTask.jobId}")
    //            pay(price).map(_.flatMap {
    //              case Left(error) =>
    //                tookanService.deleteTask(tookanTask.jobId)
    //                  .map(_ => Left(ServerError(error.message, Option(error.errorType))))
    //              case Right(charge) =>
    //                saveTask(basePrice, Option(charge.getId));
    //            }).getOrElse(Future(Left(ServerError("User doesn't set payment sources"))))
    //          case _ =>
    //            Logger.debug(s"Task ${tookanTask.jobId} is free for user $userId")
    //            saveTask(basePrice)
    //        }
    //      }
    //    }
    //
    //    def createTaskInternal(vehicle: VehiclesRow, user: UsersRow) = {
    //      tookanService.createAppointment(dto.pickupName, dto.pickupPhone, dto.pickupAddress, dto.description,
    //        dto.pickupDateTime, Option(dto.pickupLatitude), Option(dto.pickupLongitude), Option(user.email),
    //        getVehicleMetadata(mapToVehicleInfo(vehicle), dto.hasInteriorCleaning))
    //        .flatMap {
    //          case Left(error) =>
    //            Future.successful(Left(ServerError(error.message)))
    //          case Right(task) =>
    //            processPayment(task, user)
    //        }
    //    }
    //
    //    val vehicleQuery = for {
    //      v <- Vehicles if v.id === dto.vehicleId && v.userId === userId
    //    } yield v
    //    val userQuery = for {
    //      user <- Users if user.id === userId
    //    } yield user
    //    db.run(vehicleQuery.result.headOption zip userQuery.result.headOption).flatMap { resultRow =>
    //      val taskCreateResultOpt = for {
    //        vehicle <- resultRow._1
    //        user <- resultRow._2
    //      } yield createTaskInternal(vehicle, user)
    //      taskCreateResultOpt.getOrElse(Future(Left(ServerError("Invalid vehicle id or user not found"))))
    //    }
  }

  override def refreshTask(taskId: Long): Unit = {
    system.actorOf(TasksActor.props(tookanService, dbConfigProvider, pushNotificationService)) ! RefreshTaskData(taskId)
  }

  override def pendingTasks(userId: Int): Future[Seq[(JobsRow, Option[AgentsRow], VehiclesRow)]] = {
    val taskQuery = for {
      ((job, agent), vehicle) <- Jobs joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if job.jobStatus === Successful.code && job.submitted === false && job.userId === userId
    } yield (job, agent, vehicle)
    db.run(taskQuery.result)
  }

  private def getVehicleMetadata[V <: AbstractVehicle](vehicle: V): Seq[Metadata] = {
    val metadata: Seq[Metadata] = Seq(
      Metadata(Metadata.maker, vehicle.maker),
      Metadata(Metadata.model, vehicle.model),
      Metadata(Metadata.year, vehicle.year.toString),
      Metadata(Metadata.color, vehicle.color)
      //TODO: include services
    )
    vehicle.licPlate.map(plateNumber => metadata :+ Metadata(Metadata.licPlate, plateNumber))
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