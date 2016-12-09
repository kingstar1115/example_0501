package controllers

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import commons.enums.TaskStatuses.Successful
import commons.enums.{TaskStatuses, ValidationError => VError}
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
import services.internal.settings.SettingsService
import services.internal.tasks.TasksService
import services.internal.tasks.TasksService._
import services.{StripeService, _}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class TasksController @Inject()(val tokenStorage: TokenStorage,
                                dbConfigProvider: DatabaseConfigProvider,
                                tookanService: TookanService,
                                stripeService: StripeService,
                                config: Configuration,
                                settingsService: SettingsService,
                                taskService: TasksService) extends BaseController {

  implicit val agentDtoFormat = Json.format[AgentDto]
  implicit val taskListDtoFormat = Json.format[TaskListDto]
  implicit val taskDetailsDtoFormat = Json.format[TaskDetailsDto]
  implicit val tipDtoFormat = Json.format[TipDto]
  implicit val completeTaskDtoFormat = Json.format[CompleteTaskDto]

  val db = dbConfigProvider.get.db

  def createTask = authorized.async(BodyParsers.parse.json) { request =>
    processRequestF[TaskDto](request.body) { dto =>
      val userId = request.token.get.userInfo.id
      taskService.createTask(dto, userId) map {
        case Left(error) => badRequest(error.message)
        case Right(tookanTask) => ok(tookanTask)
      }
    }
  }

  def createCustomerTask = authorized.async(BodyParsers.parse.json) { request =>
    processRequestF[CustomerTaskDto](request.body) { implicit dto =>
      taskService.createTaskForCustomer(request.token.get.userInfo.id) map {
        case Left(error) => badRequest(error.message)
        case Right(tookanTask) => ok(tookanTask)
      }
    }
  }

  def createAnonymousTask = Action.async(BodyParsers.parse.json) { request =>
    processRequestF[AnonymousTaskDto](request.body) { implicit dto =>
      taskService.createTaskForAnonymous(dto) map {
        case Left(error) => badRequest(error.message)
        case Right(tookanTask) => ok(tookanTask)
      }
    }
  }

  def cancelTask(id: Long) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val selectQuery = for {
      job <- Jobs
      if job.userId === userId && job.jobStatus.inSet(TaskStatuses.cancelableStatuses) && job.jobId === id
    } yield job

    db.run(selectQuery.result.headOption).flatMap {
      _.map { job =>

        val refundResult = job.chargeId.map { chargeId =>
          Logger.debug(s"Refunding charge $chargeId for customer ${request.token.get.userInfo.email}")
          stripeService.refund(chargeId).map {
            case Left(error) =>
              Logger.debug(s"Can't refund money for job $id")
              Option(chargeId)
            case Right(refund) =>
              Logger.debug(s"Refunded money for task with $id id")
              Option.empty
          }
        }.getOrElse(Future.successful(Option.empty))

        refundResult.flatMap { chargeOpt =>
          tookanService.updateTaskStatus(id, TaskStatuses.Cancel)
          val updateQuery = for {
            job <- Jobs if job.jobId === id && job.userId === userId
          } yield (job.jobStatus, job.chargeId)
          db.run(updateQuery.update(TaskStatuses.Cancel.code, chargeOpt))
            .map(_ => success)
        }
      }.getOrElse(Future.successful(badRequest(s"Can't cancel task with $id id")))
    }
  }

  def getPendingTask = authorized.async { request =>
    taskService.pendingTasks(request.token.get.userInfo.id).map { resultSet =>
      val pendingTask = resultSet.headOption.map { row =>
        implicit val job = row._1
        mapToDto(row)(toListDto)
      }
      ok(pendingTask)
    }
  }

  def getPendingTasks = authorized.async { request =>
    taskService.pendingTasks(request.token.get.userInfo.id).map { resultSet =>
      val pendingTasks = resultSet.map { row =>
        implicit val job = row._1
        mapToDto(row)(toListDto)
      }
      ok(pendingTasks)
    }
  }

  def completeTask = authorized.async(BodyParsers.parse.json) { request =>
    processRequestF[CompleteTaskDto](request.body) { dto =>

      val userId = request.token.get.userInfo.id
      val taskQuery = for {
        job <- Jobs if job.jobId === dto.jobId && job.jobStatus === Successful.code && job.submitted === false && job.userId === userId
      } yield job
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
                db.run(taskQuery.map(task => (task.tip, task.submitted)).update((tip.amount, true)))
                  .map(_ => success)
              case Left(error) =>
                Logger.debug(s"Failed to charge tip: ${error.message}")
                Future(badRequest(error.message, error.errorType))
            }
          }.getOrElse {
            db.run(taskQuery.map(_.submitted).update(true).map(_ => success))
          }
        case _ =>
          Logger.debug(s"Task with id ${dto.jobId} was not found for submitting")
          Future(badRequest("Can't find task to submit"))
      }
    }
  }

  def listTasks(offset: Int, limit: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val inStatuses = request.queryString.get("status").map(_.map(_.toInt).toSet)
    val notInStatuses = request.queryString.get("ignore").map(_.map(_.toInt).toSet)
    val submitted = request.getQueryString("submitted").map(_.toBoolean)

    val baseQuery = for {
      ((job, agent), vehicle) <- Jobs joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if job.userId === userId
    } yield (job, agent, vehicle)
    val filteredByInStatus = inStatuses.map(s => baseQuery.filter(_._1.jobStatus.inSet(s))).getOrElse(baseQuery)
    val filteredByNotInStatus = notInStatuses.map(s => filteredByInStatus.filterNot(_._1.jobStatus.inSet(s))).getOrElse(filteredByInStatus)
    val listQuery = submitted.map(s => filteredByInStatus.filter(_._1.submitted === s)).getOrElse(filteredByNotInStatus)

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
    taskService.refreshTask(formData.jobId)
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
      job.hasInteriorCleaning, job.price, job.latitude, job.longitude, job.promotion, job.tip)
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
}

object TasksController {

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
                            hasInteriorCleaning: Boolean,
                            price: Int,
                            latitude: BigDecimal,
                            longitude: BigDecimal,
                            promotion: Int,
                            tip: Int)

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
      (__ \ "hasInteriorCleaning").read[Boolean] and
      (__ \ "vehicleId").read[Int] and
      (__ \ "promotion").readNullable[Int]
        .filter(ValidationError("Value must be greater than 0"))(_.map(_ > 0).getOrElse(true))
    ) (TaskDto.apply _)


  implicit val anonymousPaymentDetailsFormat = Json.format[AnonymousPaymentDetails]
  implicit val anonymousVehicleDetailsFormat = Json.format[AnonymousVehicleDetailsDto]
  implicit val customerPaymentDetailsReads: Reads[CustomerPaymentDetails] = (
    (__ \ "promotion").readNullable[Int] and
      (__ \ "hasInteriorCleaning").read[Boolean] and
      (__ \ "token").readNullable[String] and
      (__ \ "cardId").readNullable[String]
    ) (CustomerPaymentDetails.apply _)
    .filter(ValidationError("Token or card id must be provided"))(dto => dto.token.isDefined || dto.cardId.isDefined)

  implicit val customerTaskReads: Reads[CustomerTaskDto] = (
    (__ \ "description").read[String] and
      (__ \ "name").read[String] and
      (__ \ "email").readNullable[String] and
      (__ \ "phone").read[String] and
      (__ \ "address").read[String] and
      (__ \ "latitude").read[Double] and
      (__ \ "longitude").read[Double] and
      (__ \ "dateTime").read[LocalDateTime](dateTimeReads) and
      (__ \ "vehicleId").read[Int] and
      (__ \ "paymentDetails").read[CustomerPaymentDetails]
    ) (CustomerTaskDto.apply _)

  implicit val anonymousTaskReads: Reads[AnonymousTaskDto] = (
    (__ \ "description").read[String] and
      (__ \ "name").read[String] and
      (__ \ "email").readNullable[String] and
      (__ \ "phone").read[String] and
      (__ \ "address").read[String] and
      (__ \ "latitude").read[Double] and
      (__ \ "longitude").read[Double] and
      (__ \ "dateTime").read[LocalDateTime](dateTimeReads) and
      (__ \ "vehicle").read[AnonymousVehicleDetailsDto] and
      (__ \ "paymentDetails").read[AnonymousPaymentDetails]
    ) (AnonymousTaskDto.apply _)

  case class TipDto(amount: Int,
                    cardId: Option[String],
                    token: Option[String])

  case class CompleteTaskDto(jobId: Long,
                             tip: Option[TipDto])

  case class TaskHook(jobId: Long,
                      jobStatus: Int)

}


