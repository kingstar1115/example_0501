package controllers

import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import actors.TasksActor
import actors.TasksActor.RefreshTaskData
import akka.actor.ActorSystem
import controllers.TasksController._
import controllers.VehiclesController._
import controllers.base.{BaseController, ListResponse}
import models.Tables._
import play.api.data.validation.ValidationError
import play.api.data.{Form, Forms}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{Reads, _}
import play.api.mvc.{Action, BodyParsers, Result}
import play.api.{Configuration, Logger}
import security.TokenStorage
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
  implicit val jobDtoFormat = Json.format[JobDto]
  implicit val tipDtoFormat = Json.format[TipDto]
  implicit val completeTaskDtoFormat = Json.format[CompleteTaskDto]

  val db = dbConfigProvider.get.db

  def createTask = authorized.async(BodyParsers.parse.json) { request =>
    processRequest[TaskDto](request.body) { dto =>
      val token = request.token.get
      val userId = token.userInfo.id

      def processPayment(tookanTask: AppointmentResponse, customerId: String) = {
        stripeService.charge(calculatePrice(dto.cleaningType, dto.hasInteriorCleaning),
          StripeService.PaymentSource(customerId, dto.token), tookanTask.jobId).flatMap {
          case Left(error) =>
            tookanService.deleteTask(tookanTask.jobId)
              .map(response => badRequest(error.message, error.errorType))
          case Right(charge) =>
            val insertQuery = (
              Jobs.map(job => (job.jobId, job.jobToken, job.description, job.userId, job.scheduledTime, job.vehicleId))
                returning Jobs.map(_.id)
                += ((tookanTask.jobId, tookanTask.jobToken, dto.description, userId, Timestamp.valueOf(dto.pickupDateTime), dto.vehicleId))
              )
            db.run(insertQuery)
              .map { id =>
                updateTask(tookanTask.jobId)
                ok(tookanTask)
              }
        }
      }

      def createTaskInternal(vehicle: VehiclesRow, user: UsersRow) = {
        tookanService.createAppointment(dto.pickupName, dto.pickupPhone, dto.pickupAddress, dto.description,
          dto.pickupDateTime, Option(dto.pickupLatitude), Option(dto.pickupLongitude), Option(token.userInfo.email),
          TookanService.Metadata.getVehicleMetadata(vehicle))
          .flatMap {
            case Left(error) => wrapInFuture(badRequest(error))
            case Right(task) => processPayment(task, user.stripeId.get)
          }
      }

      val vehicleQuery = for {
        v <- Vehicles if v.id === dto.vehicleId && v.userId === userId
      } yield v
      val userQuery = for {
        user <- Users if user.id === userId && user.stripeId.isDefined
      } yield user
      db.run(vehicleQuery.result.headOption zip userQuery.result.headOption).flatMap { resultRow =>
        val maybeEventualResult: Option[Future[Result]] = for {
          vehicle <- resultRow._1
          user <- resultRow._2
        } yield createTaskInternal(vehicle, user)
        maybeEventualResult.getOrElse(Future(badRequest("Invalid vehicle id or user doesn't set payment sources")))
      }
    }
  }

  def getPendingTask = authorized.async { request =>
    val taskQuery = for {
      ((job, agent), vehicle) <- Jobs joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if job.completed === true && job.submitted === false && job.userId === request.token.get.userInfo.id
    } yield (job, agent, vehicle)
    db.run(taskQuery.result.headOption).map(jobOption => ok(jobOption.map(mapToDto)))
  }

  def completeTask = authorized.async(BodyParsers.parse.json) { request =>
    processRequest[CompleteTaskDto](request.body) { dto =>
      val userId = request.token.get.userInfo.id
      val updateQuery = for {
        job <- Jobs if job.jobId === dto.jobId && job.completed === true && job.submitted === false && job.userId === userId
      } yield job.submitted
      val userQuery = for {
        user <- Users if user.id === userId && user.stripeId.isDefined
      } yield user
      db.run(updateQuery.update(true) zip userQuery.result.head).flatMap { resultRow =>
        resultRow._1 match {
          case 1 =>
            Logger.info(s"Job with JobId: ${dto.jobId} updated!")
            dto.tip.map(tip => stripeService.charge(tip.amount,
              StripeService.PaymentSource(resultRow._2.stripeId.get, tip.token), dto.jobId))
              .map(_.map {
                case Right(charge) =>
                  Logger.info(s"Tip charged. Charge id: ${charge.getId}")
                  NoContent
                case Left(error) =>
                  Logger.info(s"Failed to charge tip: ${error.message}")
                  NoContent
              })
              .getOrElse(Future.successful(NoContent))
          case _ =>
            Logger.info(s"Failed to update job with JobId: ${dto.jobId}")
            wrapInFuture(badRequest(s"Failed to update job with JobId: ${dto.jobId}"))
        }
      }
    }
  }

  def tasksHistory(offset: Int, limit: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val listQuery = for {
      ((job, agent), vehicle) <- Jobs joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if job.userId === userId
    } yield (job, agent, vehicle)
    db.run(listQuery.length.result zip listQuery.sortBy(_._1.createdDate.desc).take(limit).drop(offset).result)
      .map { result =>
        val jobs = result._2.map(mapToDto).toList
        ok(ListResponse(jobs, limit, offset, result._1))
      }
  }

  def onTaskUpdate = Action { implicit request =>
    Logger.info("Task update web hook")
    val formData = Form(Forms.single("job_id" -> Forms.number)).bindFromRequest()
    updateTask(formData.get)
    ok("Ok")
  }

  def mapToDto(row: (JobsRow, Option[AgentsRow], VehiclesRow)) = {
    val agent = row._2.map(agent => AgentDto(agent.name, agent.fleetImage)).orElse(None)
    val images = row._1.images.map(_.split(";").toList).getOrElse(List.empty[String])
    val vehicle = new VehicleDto(Some(row._3.id), row._3.makerId, row._3.makerNiceName, row._3.modelId,
      row._3.modelNiceName, row._3.yearId, row._3.year, Option(row._3.color), row._3.licPlate)
    JobDto(row._1.jobId, row._1.scheduledTime.toLocalDateTime, agent, images, vehicle)
  }

  private def updateTask(jobId: Long) = {
    system.actorOf(TasksActor.props(tookanService, dbConfigProvider)) ! RefreshTaskData(jobId)
  }
}

object TasksController {

  def calculatePrice(index: Int, hasInteriorCleaning: Boolean) = {
    val price = index match {
      case 0 => 2000
      case 1 => 2500
      case 2 => 3000
      case _ => 0
    }
    if (hasInteriorCleaning) price + 500 else price
  }

  case class AgentDto(name: String,
                      picture: String)

  case class JobDto(jobId: Long,
                    scheduledDateTime: LocalDateTime,
                    agent: Option[AgentDto],
                    images: List[String],
                    vehicle: VehicleDto)

  case class TaskDto(token: Option[String],
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
                     vehicleId: Int)

  val dateTimeReads = localDateTimeReads(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"))
  implicit val taskDtoReads: Reads[TaskDto] = (
    (__ \ "token").readNullable[String] and
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
      (__ \ "vehicleId").read[Int]
    ) (TaskDto.apply _)

  case class TipDto(amount: Int,
                    token: Option[String])

  case class CompleteTaskDto(jobId: Long,
                             tip: Option[TipDto])

}


