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
import play.api.data.{Form, Forms}
import play.api.data.validation.ValidationError
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{Reads, _}
import play.api.mvc.{Action, BodyParsers}
import play.api.{Configuration, Logger}
import security.TokenStorage
import services.TookanService.AppointmentResponse
import services.{StripeService, TookanService}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try


class TasksController @Inject()(val tokenStorage: TokenStorage,
                                dbConfigProvider: DatabaseConfigProvider,
                                tookanService: TookanService,
                                stripeService: StripeService,
                                config: Configuration,
                                system: ActorSystem) extends BaseController {

  implicit val agentDtoFormat = Json.format[AgentDto]
  implicit val jobDtoFormat = Json.format[JobDto]

  val db = dbConfigProvider.get.db

  def createTask = authorized.async(BodyParsers.parse.json) { request =>
    def onValidationSuccess(dto: TaskDto) = {

      val userId = request.token.get.userInfo.id

      def checkVehicle = {
        val vehicleQuery = for {
          v <- Vehicles if v.id === dto.vehicleId && v.userId === userId
        } yield v
        db.run(vehicleQuery.length.result)
      }

      def processPayment(tookanTask: AppointmentResponse) = {
        stripeService.charge(calculatePrice(dto.cleaningType, dto.hasInteriorCleaning), dto.token, tookanTask.jobId)
          .flatMap {
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

      tookanService.createAppointment(dto.pickupName, dto.pickupPhone, dto.pickupAddress,
        dto.description, dto.pickupDateTime, Some(dto.pickupLongitude), Some(dto.pickupLatitude), None)
        .flatMap {
          case Left(error) => wrapInFuture(badRequest(error))
          case Right(task) => checkVehicle.flatMap {
            case 1 => processPayment(task)
            case _ => wrapInFuture(badRequest("Invalid vehicle id"))
          }
        }
    }

    request.body.validate[TaskDto]
      .fold(jsonValidationFailedFuture, onValidationSuccess)
  }

  def tasksHistory(offset: Int, limit: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val listQuery = for {
      ((job, agent), vehicle) <- Jobs joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if job.userId === userId
    } yield (job, agent, vehicle)
    db.run(listQuery.length.result zip listQuery.sortBy(_._1.createdDate.desc).take(limit).drop(offset).result)
      .map { result =>
        val jobs = result._2.map { row =>
          val agent = row._2.map(agent => AgentDto(agent.name, agent.fleetImage)).orElse(None)
          val images = row._1.images.map(_.split(";").toList).getOrElse(List.empty[String])
          val vehicle = new VehicleDto(Some(row._3.id), row._3.makerId, row._3.makerNiceName, row._3.modelId,
            row._3.modelNiceName, row._3.yearId, row._3.year, row._3.color, row._3.licPlate)
          JobDto(row._1.jobId, row._1.scheduledTime.toLocalDateTime, agent, images, vehicle)
        }.toList
        ok(ListResponse(jobs, limit, offset, result._1))
      }
  }

  def onTaskUpdate = Action { implicit request =>
    Logger.info(request.body.toString)
    val form = Form(Forms.single("job_id" -> Forms.number))
    val formData = form.bindFromRequest()
    updateTask(formData.get)
    ok("Ok")
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

  case class TaskDto(token: String,
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
    (JsPath \ "token").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "email").readNullable[String](email) and
      (JsPath \ "phone").read[String] and
      (JsPath \ "address").read[String] and
      (JsPath \ "latitude").read[Double] and
      (JsPath \ "longitude").read[Double] and
      (JsPath \ "dateTime").read[LocalDateTime](dateTimeReads) and
      (JsPath \ "cleaningType").read[Int]
        .filter(ValidationError("Value must be in range from 0 to 2"))(v => v >= 0 && v <= 2) and
      (JsPath \ "hasInteriorCleaning").read[Boolean] and
      (JsPath \ "vehicleId").read[Int]
    ) (TaskDto.apply _)
}


