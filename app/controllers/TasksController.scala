package controllers

import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import actors.TasksActor
import actors.TasksActor.RefreshTaskData
import akka.actor.ActorSystem
import controllers.TasksController._
import controllers.base.{BaseController, ListResponse}
import models.Tables._
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

      def processPayment(tookanTask: AppointmentResponse) = {
        stripeService.charge(calculatePrice(dto.cleaningType, dto.hasInteriorCleaning), dto.token, tookanTask.jobId)
          .flatMap {
            case Left(error) =>
              tookanService.deleteTask(tookanTask.jobId)
                .map(response => badRequest(error.message, error.errorType))
            case Right(charge) =>
              val insertQuery = (
                Jobs.map(job => (job.jobId, job.jobToken, job.description, job.userId, job.scheduledTime))
                  returning Jobs.map(_.id)
                  += ((tookanTask.jobId, tookanTask.jobToken, dto.description, request.token.get.userInfo.id, Timestamp.valueOf(dto.pickupDateTime)))
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
          case Right(task) => processPayment(task)
        }
    }

    request.body.validate[TaskDto]
      .fold(jsonValidationFailedFuture, onValidationSuccess)
  }

  def tasksHistory(offset: Int, limit: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val listQuery = for {
      (job, agent) <- Jobs joinLeft Agents on (_.agentId === _.id)
      if job.userId === userId
    } yield (job, agent)
    db.run(listQuery.length.result zip listQuery.sortBy(_._1.createdDate.desc).take(limit).drop(offset).result)
      .map { result =>
        val jobs = result._2.map { row =>
          val agent = row._2.map(agent => AgentDto(agent.name, agent.fleetImage)).orElse(None)
          val images = row._1.images.map(_.split(";").toList).getOrElse(List.empty[String])
          JobDto(row._1.jobId, row._1.scheduledTime.toLocalDateTime, agent, images)
        }.toList
        ok(ListResponse(jobs, limit, offset, result._1))
      }
  }

  def onTaskUpdate = Action(BodyParsers.parse.json) { request =>
    Logger.info(request.body.toString)
    (request.body \\ "job_id").headOption
      .map { value =>
        val jobId = value match {
          case s: JsString => Try(Option(s.value.toLong)).getOrElse(None)
          case n: JsNumber => Try(Option(n.as[Long])).getOrElse(None)
          case _ => None
        }
        jobId.map { id =>
          updateTask(id)
          ok("Updated")
        }.getOrElse(badRequest("Can't parse job id"))
      }.getOrElse(badRequest("Can't parse job id"))
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
                    images: List[String])

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
                     hasInteriorCleaning: Boolean)

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
      (JsPath \ "hasInteriorCleaning").read[Boolean]
    ) (TaskDto.apply _)
}


