package controllers

import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import actors.TasksActor
import actors.TasksActor.RefreshTaskData
import akka.actor.ActorSystem
import commons.enums.TaskStatuses.Successful
import commons.enums.{PaymentMethods, TaskStatuses, ValidationError => VError}
import controllers.TasksController._
import controllers.VehiclesController._
import controllers.base.{BaseController, ListResponse}
import models.Tables._
import play.api.data.Forms.{email => _, _}
import play.api.data.validation.ValidationError
import play.api.data.{Form, Forms}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{Reads, _}
import play.api.mvc.{Action, BodyParsers}
import play.api.{Configuration, Logger}
import security.TokenStorage
import services.StripeService.ErrorResponse
import services.TookanService.AppointmentResponse
import services.{StripeService, _}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class TasksController @Inject()(val tokenStorage: TokenStorage,
                                dbConfigProvider: DatabaseConfigProvider,
                                tookanService: TookanService,
                                stripeService: StripeService,
                                config: Configuration,
                                system: ActorSystem) extends BaseController {

  implicit val agentDtoFormat = Json.format[AgentDto]
  implicit val taskListDtoFormat = Json.format[TaskListDto]
  implicit val taskDetailsDtoFormat = Json.format[TaskDetailsDto]
  implicit val tipDtoFormat = Json.format[TipDto]
  implicit val completeTaskDtoFormat = Json.format[CompleteTaskDto]

  val db = dbConfigProvider.get.db

  def createTask = authorized.async(BodyParsers.parse.json) { request =>
    processRequest[TaskDto](request.body) { dto =>
      val token = request.token.get
      val userId = token.userInfo.id

      def processPayment(tookanTask: AppointmentResponse, user: UsersRow) = {
        def saveTask(price: Int = 0) = {
          val insertQuery = (
            Jobs.map(job => (job.jobId, job.userId, job.scheduledTime, job.vehicleId, job.paymentMethod,
              job.cleaningType, job.hasInteriorCleaning, job.price, job.latitude, job.longitude))
              returning Jobs.map(_.id)
              += ((tookanTask.jobId, userId, Timestamp.valueOf(dto.pickupDateTime), dto.vehicleId,
              dto.cardId.getOrElse(PaymentMethods.ApplePay.toString), dto.cleaningType, dto.hasInteriorCleaning,
              price, dto.pickupLatitude, dto.pickupLongitude))
            )
          db.run(insertQuery).map { id =>
            updateTask(tookanTask.jobId)
            ok(tookanTask)
          }
        }

        def pay(price: Int) = {
          dto.token
            .map(token => Option(stripeService.charge(price, token, tookanTask.jobId)))
            .getOrElse {
              user.stripeId.map { stripeId =>
                val paymentSource = StripeService.PaymentSource(stripeId, dto.cardId)
                stripeService.charge(price, paymentSource, tookanTask.jobId)
              }
            }
        }

        val price = calculatePrice(dto.cleaningType, dto.hasInteriorCleaning, dto.promotion)
        price match {
          case x if x > 50 =>
            Logger.debug(s"Charging $price from user $userId for task ${tookanTask.jobId}")
            pay(price).map(_.flatMap {
              case Left(error) =>
                tookanService.deleteTask(tookanTask.jobId)
                  .map(response => badRequest(error.message, error.errorType))
              case Right(charge) =>
                saveTask(price);
            }).getOrElse(Future(badRequest("User doesn't set payment sources")))
          case _ =>
            Logger.debug(s"Task ${tookanTask.jobId} is free for user $userId")
            saveTask()
        }
      }

      def createTaskInternal(vehicle: VehiclesRow, user: UsersRow) = {
        tookanService.createAppointment(dto.pickupName, dto.pickupPhone, dto.pickupAddress, dto.description,
          dto.pickupDateTime, Option(dto.pickupLatitude), Option(dto.pickupLongitude), Option(token.userInfo.email),
          TookanService.Metadata.getVehicleMetadata(vehicle, dto.hasInteriorCleaning))
          .flatMap {
            case Left(error) =>
              wrapInFuture(badRequest(error))
            case Right(task) =>
              processPayment(task, user)
          }
      }

      val vehicleQuery = for {
        v <- Vehicles if v.id === dto.vehicleId && v.userId === userId
      } yield v
      val userQuery = for {
        user <- Users if user.id === userId
      } yield user
      db.run(vehicleQuery.result.headOption zip userQuery.result.headOption).flatMap { resultRow =>
        val taskCreateResultOpt = for {
          vehicle <- resultRow._1
          user <- resultRow._2
        } yield createTaskInternal(vehicle, user)
        taskCreateResultOpt.getOrElse(Future(badRequest("Invalid vehicle id or user not found")))
      }
    }
  }

  def getPendingTask = authorized.async { request =>
    val taskQuery = for {
      ((job, agent), vehicle) <- Jobs joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if job.jobStatus === Successful.code && job.submitted === false && job.userId === request.token.get.userInfo.id
    } yield (job, agent, vehicle)
    db.run(taskQuery.result.headOption).map { rowOpt =>
      val pendingTaskOpt = rowOpt.map { row =>
        implicit val job = row._1
        mapToDto(row)(toListDto)
      }
      ok(pendingTaskOpt)
    }
  }

  def completeTask = authorized.async(BodyParsers.parse.json) { request =>
    processRequest[CompleteTaskDto](request.body) { dto =>

      val userId = request.token.get.userInfo.id
      val taskQuery = for {
        job <- Jobs if job.jobId === dto.jobId && job.jobStatus === Successful.code && job.submitted === false && job.userId === userId
      } yield job.submitted
      val userQuery = for {
        user <- Users if user.id === userId
      } yield user

      db.run(taskQuery.exists.result).flatMap {
        case true =>
          dto.tip.map { tip =>
            val paymentResult = tip.token
              .map { token =>
                Logger.debug(s"Charging tip for task ${dto.jobId} from token $token")
                stripeService.charge(tip.amount, token, dto.jobId)
              }
              .getOrElse {
                db.run(userQuery.result.head).flatMap { user =>
                  user.stripeId.map { stripeId =>
                    val paymentSource = StripeService.PaymentSource(user.stripeId.get, tip.cardId)
                    stripeService.charge(tip.amount, paymentSource, dto.jobId)
                  }.getOrElse {
                    Future(Left(ErrorResponse("User doesn't set a payment method", VError)))
                  }
                }
              }

            paymentResult.flatMap {
              case Right(charge) =>
                db.run(taskQuery.update(true))
                  .map(_ => success)
              case Left(error) =>
                Logger.debug(s"Failed to charge tip: ${error.message}")
                Future(badRequest(error.message, error.errorType))
            }
          }.getOrElse {
            db.run(taskQuery.update(true).map(_ => success))
          }
        case _ =>
          Logger.debug(s"Task with id ${dto.jobId} was not found for submitting")
          Future(badRequest("Can't find task to submit"))
      }
    }
  }

  def listTasks(offset: Int, limit: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val status = request.getQueryString("status").map(_.toInt)
    val submitted = request.getQueryString("submitted").map(_.toBoolean)

    val baseQuery = for {
      ((job, agent), vehicle) <- Jobs joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if job.userId === userId
    } yield (job, agent, vehicle)
    val filteredByStatus = status.map(s => baseQuery.filter(_._1.jobStatus === s)).getOrElse(baseQuery)
    val listQuery = submitted.map(s => filteredByStatus.filter(_._1.submitted === s)).getOrElse(filteredByStatus)

    db.run(listQuery.length.result zip listQuery.sortBy(_._1.createdDate.desc).take(limit).drop(offset).result)
      .map { result =>
        val jobs = result._2.map { row =>
          implicit val job = row._1
          mapToDto(row)(toListDto)
        }.toList
        ok(ListResponse(jobs, limit, offset, result._1))
      }
  }

  def getTask(id: Long) = authorized.async { request =>
    val selectQuery = for {
      ((job, agent), vehicle) <- Jobs joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if job.userId === request.token.get.userInfo.id && job.jobId === id
    } yield (job, agent, vehicle)
    db.run(selectQuery.result.headOption).map(_.map { row =>
      implicit val job = row._1
      ok(mapToDto(row)(toDetailsDto))
    }.getOrElse(notFound))
  }

  def onTaskUpdate = Action { implicit request =>
    val formData = Form(mapping(
      "job_id" -> Forms.longNumber,
      "job_status" -> Forms.number
    )(TaskHook.apply)(TaskHook.unapply)).bindFromRequest().get
    Logger.debug(s"Task update web hook. ${formData.toString}")
    updateTask(formData.jobId)
    NoContent
  }

  def getAgentCoordinates(fleetId: Long) = authorized.async { request =>
    tookanService.getAgentCoordinates(fleetId).map {
      case Right(coordinates) =>
        ok(coordinates)
      case Left(e) =>
        badRequest(e.message)
    }
  }

  def getActiveTask = authorized.async { request =>
    val selectQuery = for {
      ((job, agent), vehicle) <- Jobs joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if job.userId === request.token.get.userInfo.id && job.jobStatus.inSet(TaskStatuses.activeStatuses)
    } yield (job, agent, vehicle)
    db.run(selectQuery.sortBy(_._1.scheduledTime.asc).result.headOption).map { rowOpt =>
      val activeTaskOpt = rowOpt.map { row =>
        implicit val job = row._1
        mapToDto(row)(toDetailsDto)
      }
      ok(activeTaskOpt)
    }
  }

  def toListDto(agent: Option[AgentDto], vehicle: VehicleDto)(implicit job: JobsRow) = {
    TaskListDto(job.jobId, job.scheduledTime.toLocalDateTime, agent, getJobImages(job), vehicle,
      job.jobStatus, job.submitted)
  }

  def toDetailsDto(agent: Option[AgentDto], vehicle: VehicleDto)(implicit job: JobsRow) = {
    TaskDetailsDto(job.jobId, job.scheduledTime.toLocalDateTime, agent, getJobImages(job), vehicle, job.jobStatus,
      job.submitted, job.teamName, job.jobAddress, job.jobPickupPhone, job.customerPhone, job.paymentMethod,
      job.cleaningType, job.hasInteriorCleaning, job.price, job.latitude, job.longitude)
  }

  private def mapToDto[D](row: (JobsRow, Option[AgentsRow], VehiclesRow))(mapper: (Option[AgentDto], VehicleDto) => D) = {
    val car = row._3
    val agent = row._2
      .map(agent => AgentDto(agent.fleetId, agent.name, agent.fleetImage, agent.phone))
      .orElse(None)
    val vehicle = new VehicleDto(Some(car.id), car.makerId, car.makerNiceName, car.modelId,
      car.modelNiceName, car.yearId, car.year, Option(car.color), car.licPlate)
    mapper(agent, vehicle)
  }

  private def getJobImages(job: JobsRow) = {
    job.images.map(_.split(";").filter(_.nonEmpty).toList).getOrElse(List.empty[String])
  }

  private def updateTask(jobId: Long) = {
    system.actorOf(TasksActor.props(tookanService, dbConfigProvider)) ! RefreshTaskData(jobId)
  }
}

object TasksController {

  def calculatePrice(index: Int, hasInteriorCleaning: Boolean, discount: Option[Int] = None) = {
    def calculateCarWashingPrice() = {
      index match {
        case 0 => 2000
        case 1 => 2500
        case 2 => 3000
        case _ => 0
      }
    }

    val priceBeforeDiscount = if (hasInteriorCleaning) calculateCarWashingPrice() + 500 else calculateCarWashingPrice()
    discount.map { discountAmount =>
      Logger.debug(s"Washing price: $priceBeforeDiscount. Discount: $discountAmount")
      val discountedPrice = priceBeforeDiscount - discountAmount
      if (discountedPrice > 0 && discountedPrice < 50) 0 else discountedPrice
    }.getOrElse(priceBeforeDiscount)
  }

  case class AgentDto(fleetId: Long,
                      name: String,
                      picture: String,
                      phone: String)

  case class TaskListDto(jobId: Long,
                         scheduledDateTime: LocalDateTime,
                         agent: Option[AgentDto],
                         images: List[String],
                         vehicle: VehicleDto,
                         status: Int,
                         submitted: Boolean)

  case class TaskDetailsDto(jobId: Long,
                            scheduledDateTime: LocalDateTime,
                            agent: Option[AgentDto],
                            images: List[String],
                            vehicle: VehicleDto,
                            status: Int,
                            submitted: Boolean,
                            teamName: Option[String],
                            jobAddress: Option[String],
                            jobPickupPhone: Option[String],
                            customerPhone: Option[String],
                            paymentMethod: String,
                            cleaningType: Int,
                            hasInteriorCleaning: Boolean,
                            price: Int,
                            latitude: BigDecimal,
                            longitude: BigDecimal)

  case class TaskDto(token: Option[String],
                     cardId: Option[String],
                     description: String,
                     pickupName: String,
                     pickupEmail: Option[String],
                     pickupPhone: String,
                     pickupAddress: String,
                     pickupLatitude: Double,
                     pickupLongitude: Double,
                     pickupDateTime: LocalDateTime,
                     cleaningType: Int,
                     hasInteriorCleaning: Boolean,
                     vehicleId: Int,
                     promotion: Option[Int])

  val dateTimeReads = localDateTimeReads(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"))
  implicit val taskDtoReads: Reads[TaskDto] = (
    (__ \ "token").readNullable[String] and
      (__ \ "cardId").readNullable[String] and
      (__ \ "description").read[String] and
      (__ \ "name").read[String] and
      (__ \ "email").readNullable[String](email) and
      (__ \ "phone").read[String] and
      (__ \ "address").read[String] and
      (__ \ "latitude").read[Double] and
      (__ \ "longitude").read[Double] and
      (__ \ "dateTime").read[LocalDateTime](dateTimeReads) and
      (__ \ "cleaningType").read[Int]
        .filter(ValidationError("Value must be in range from 0 to 2"))(v => v >= 0 && v <= 2) and
      (__ \ "hasInteriorCleaning").read[Boolean] and
      (__ \ "vehicleId").read[Int] and
      (__ \ "promotion").readNullable[Int]
        .filter(ValidationError("Value must be greater than 0"))(_.map(_ > 0).getOrElse(true))
    ) (TaskDto.apply _)
    .filter(ValidationError("token or cardId must be defined"))(dto => dto.token.isDefined || dto.cardId.isDefined)

  case class TipDto(amount: Int,
                    cardId: Option[String],
                    token: Option[String])

  case class CompleteTaskDto(jobId: Long,
                             tip: Option[TipDto])

  case class TaskHook(jobId: Long,
                      jobStatus: Int)

}


