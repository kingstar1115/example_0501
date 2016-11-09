package services.internal.tasks

import java.sql.Timestamp
import javax.inject.{Inject, Singleton}

import actors.TasksActor
import actors.TasksActor.RefreshTaskData
import akka.actor.ActorSystem
import com.stripe.model.Charge
import commons.ServerError
import commons.enums.{PaymentMethods, TookanError}
import models.Tables._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import services.StripeService.ErrorResponse
import services.TookanService.{AppointmentResponse, Metadata}
import services.internal.notifications.PushNotificationService
import services.internal.settings.SettingsService
import services.internal.tasks.TasksService._
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
                                   pushNotificationService: PushNotificationService) extends TasksService {

  val db = dbConfigProvider.get.db

  override def createTaskForCustomer(userId: Int)(implicit taskDto: CustomerTaskDto): Future[Either[ServerError, AppointmentResponse]] = {
    def pay(price: Int, jobId: Long) = {
      taskDto.paymentDetails.token
        .map(token => stripeService.charge(price, token, jobId))
        .getOrElse {
          val userSelect = for {
            user <- Users if user.id === userId if user.stripeId.isDefined
          } yield user.stripeId
          db.run(userSelect.result.head).flatMap { stripeIdOpt =>
            val paymentSource = StripeService.PaymentSource(stripeIdOpt.get, taskDto.paymentDetails.cardId)
            stripeService.charge(price, paymentSource, jobId)
          }
        }
    }

    def saveTask(price: Int, chargeId: Option[String], tookanTask: AppointmentResponse) = {
      val paymentMethod = taskDto.paymentDetails.cardId.getOrElse(PaymentMethods.ApplePay.toString)
      val insertQuery = (
        Jobs.map(job => (job.jobId, job.userId, job.scheduledTime, job.vehicleId, job.paymentMethod,
          job.hasInteriorCleaning, job.price, job.latitude, job.longitude, job.promotion, job.chargeId))
          returning Jobs.map(_.id)
          += ((tookanTask.jobId, userId, Timestamp.valueOf(taskDto.dateTime), taskDto.vehicleId, paymentMethod,
          taskDto.paymentDetails.hasInteriorCleaning, price, taskDto.latitude, taskDto.longitude,
          taskDto.paymentDetails.promotion.getOrElse(0), chargeId))
        )
      db.run(insertQuery).map { id =>
        refreshTask(tookanTask.jobId)
        Right(tookanTask)
      }
    }

    val vehicleQuery = for {
      v <- Vehicles if v.id === taskDto.vehicleId && v.userId === userId
    } yield v
    db.run(vehicleQuery.result.head).flatMap { vehicle =>
      val metadata = getVehicleMetadata(vehicle, taskDto.paymentDetails.hasInteriorCleaning)
      createTask(metadata)(pay _)(saveTask _)
    }
  }

  private def getVehicleMetadata(vehicle: VehiclesRow, hasInteriorCleaning: Boolean): Seq[Metadata] = {
    val metadata: Seq[Metadata] = Seq(
      Metadata(Metadata.maker, vehicle.makerNiceName),
      Metadata(Metadata.model, vehicle.modelNiceName),
      Metadata(Metadata.year, vehicle.year.toString),
      Metadata(Metadata.color, vehicle.color),
      Metadata(Metadata.interior, hasInteriorCleaning.toString)
    )
    vehicle.licPlate.map(plateNumber => metadata :+ Metadata(Metadata.licPlate, plateNumber)).getOrElse(metadata)
  }

  override def createTaskForAnonymous(implicit taskDto: AnonymousTaskDto): Future[Either[ServerError, AppointmentResponse]] = {
    val metadata = getVehicleMetadata(taskDto.vehicle, taskDto.paymentDetails.hasInteriorCleaning)
    createTask(metadata) { (price, jobId) =>
      stripeService.charge(price, taskDto.paymentDetails.token, jobId)
    } { (price, chargeId, tookanTask) =>
      Future(Right(tookanTask))
    }
  }

  private def getVehicleMetadata(vehicleDto: AnonymousVehicleDetailsDto, hasInteriorCleaning: Boolean): Seq[Metadata] = {
    Seq(
      Metadata(Metadata.maker, vehicleDto.maker),
      Metadata(Metadata.model, vehicleDto.model),
      Metadata(Metadata.year, vehicleDto.year.toString),
      Metadata(Metadata.color, vehicleDto.color),
      Metadata(Metadata.interior, hasInteriorCleaning.toString)
    )
  }

  private def createTask(metadata: Seq[Metadata])
                        (pay: (Int, Long) => Future[Either[ErrorResponse, Charge]])
                        (saveTask: (Int, Option[String], AppointmentResponse) => Future[Either[ServerError, AppointmentResponse]])
                        (implicit dto: BaseTaskDto): Future[Either[ServerError, AppointmentResponse]] = {

    def processPayment(implicit tookanTask: AppointmentResponse) = {
      val basePrice = getBasePrice(dto.paymentDetails.hasInteriorCleaning)
      val price = calculatePrice(basePrice, dto.paymentDetails.promotion)
      price match {
        case x if x > 50 =>
          Logger.debug(s"Charging $price from user for task ${tookanTask.jobId}")
          pay(price, tookanTask.jobId).flatMap {
            case Left(error) =>
              tookanService.deleteTask(tookanTask.jobId)
                .map(response => Left(ServerError(error.message, Option(error.errorType))))
            case Right(charge) =>
              saveTask(basePrice, Option(charge.getId), tookanTask);
          }
        case _ =>
          Logger.debug(s"Task ${tookanTask.jobId} is free for user ${dto.email}")
          saveTask(basePrice, None, tookanTask);
      }
    }

    tookanService.createAppointment(dto.name, dto.phone, dto.address, dto.description,
      dto.dateTime, Option(dto.latitude), Option(dto.longitude), dto.email, metadata).flatMap {
      case Left(error) =>
        val tookanError = ServerError(error.message, Option(TookanError))
        Future.successful(Left(tookanError))
      case Right(task) =>
        processPayment(task)
    }
  }

  private def calculatePrice(priceBeforeDiscount: Int, discount: Option[Int] = None): Int = {
    discount.map { discountAmount =>
      Logger.debug(s"Washing price: $priceBeforeDiscount. Discount: $discountAmount")
      val discountedPrice = priceBeforeDiscount - discountAmount
      if (discountedPrice > 0 && discountedPrice < 50) 0 else discountedPrice
    }.getOrElse(priceBeforeDiscount)
  }

  private def getBasePrice(hasInteriorCleaning: Boolean): Int = {
    val priceSettings = settingsService.getPriceSettings
    hasInteriorCleaning match {
      case true =>
        priceSettings.carWashing + priceSettings.interiorCleaning
      case false =>
        priceSettings.carWashing
    }
  }

  override def refreshTask(taskId: Long) = {
    system.actorOf(TasksActor.props(tookanService, dbConfigProvider, pushNotificationService)) ! RefreshTaskData(taskId)
  }
}