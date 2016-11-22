package services

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}

import commons.enums.TaskStatuses.TaskStatus
import models.Tables.VehiclesRow
import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.mvc.Http.{HeaderNames, MimeTypes}
import services.TookanService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class TookanService @Inject()(ws: WSClient,
                              configuration: Configuration) {

  implicit val config = Config(
    configuration.getString("tookan.key").get,
    configuration.getInt("tookan.teamId").get,
    configuration.getInt("tookan.userId").get,
    configuration.getString("tookan.template").get)

  val BASE_URL = configuration.getString("tookan.url").get

  def createAppointment(customerName: String,
                        customerPhone: String,
                        customerAddress: String,
                        jobDescription: String,
                        pickupDateTime: LocalDateTime,
                        latitude: Option[Double],
                        longitude: Option[Double],
                        customerEmail: Option[String],
                        metadata: Seq[Metadata] = Seq.empty[Metadata]): Future[Either[TookanResponse, AppointmentResponse]] = {
    val task = AppointmentTask.default(customerName,
      customerPhone,
      customerAddress,
      jobDescription,
      pickupDateTime,
      latitude,
      longitude,
      customerEmail,
      metadata)
    createAppointment(task)
  }

  def createAppointment(task: AppointmentTask): Future[Either[TookanResponse, AppointmentResponse]] = {
    buildRequest(CreateTask)
      .post(Json.toJson(task))
      .map(response => response.convert[AppointmentResponse])
  }

  def deleteTask(jobId: Long) = {
    buildRequest(DeleteTask)
      .post(Json.toJson(DeleteTaskDto.default(jobId)))
      .map(response => response.getResponse)
  }

  def getTask(jobId: Long): Future[Either[TookanResponse, AppointmentDetails]] = {
    buildRequest(TaskDetails)
      .post(Json.obj(
        "access_token" -> config.key,
        "user_id" -> config.userId,
        "job_id" -> jobId.toString
      ))
      .map { response =>
        response.asList[AppointmentDetails] match {
          case Right(list) =>
            Right(list.head)
          case Left(e) =>
            Left(e)
        }
      }
  }

  def getTeam: Future[Either[TookanResponse, Team]] = {
    buildRequest(TeamDetails)
      .post(Json.obj(
        "access_token" -> config.key
      ))
      .map { response =>
        response.asList[Team] match {
          case Right(list) =>
            Right(list.head)
          case Left(e) =>
            Left(e)
        }
      }
  }

  def listAgents: Future[Either[TookanResponse, List[Agent]]] = {
    buildRequest(ListAgents)
      .post(Json.obj("access_token" -> config.key))
      .map(response => response.asList[Agent])
  }

  def getAgentCoordinates(fleetId: Long): Future[Either[TookanResponse, Coordinates]] = {
    buildRequest(AgentCoordinates)
      .post(Json.obj(
        "access_token" -> config.key,
        "fleet_id" -> fleetId
      ))
      .map { response =>
        response.asList[Coordinates] match {
          case Right(list) =>
            Right(list.head)
          case Left(e) =>
            Left(e)
        }
      }
  }

  def updateTaskStatus(taskId: Long, status: TaskStatus) = {
    buildRequest(UpdateTaskStatus)
      .post(Json.obj(
        "access_token" -> config.key,
        "job_id" -> taskId.toString,
        "job_status" -> status.code
      ))
  }

  private def buildRequest(path: String) = {
    ws.url(buildUrl(path))
      .withRequestTimeout(10000L)
      .withHeaders((HeaderNames.CONTENT_TYPE, MimeTypes.JSON))
  }

  private def buildUrl(path: String) = BASE_URL.concat("/").concat(path)

  implicit class WSResponseEx(wsResponse: WSResponse) {

    val jsonBody = wsResponse.json

    def getResponse: TookanResponse = jsonBody.as[TookanResponse](TookanResponse.tookanResponseFormat)

    def convert[T](implicit reads: Reads[T]): Either[TookanResponse, T] = {
      val response = getResponse
      response.status match {
        case 200 => Right((jsonBody \ "data").as[T])
        case _ => Left(response)
      }
    }

    def asList[T](implicit reads: Reads[T]): Either[TookanResponse, List[T]] = {
      val response = getResponse
      response.status match {
        case 200 => Right((jsonBody \ "data").as[List[T]])
        case _ => Left(response)
      }
    }
  }

}

object TookanService {

  val CreateTask = "create_task"
  val DeleteTask = "delete_job"
  val TaskDetails = "view_task_profile"
  val ListAgents = "view_all_fleets_location"
  val TeamDetails = "view_team"
  val AgentCoordinates = "view_all_fleets"
  val UpdateTaskStatus = "update_task_via_dashboard"

  case class Config(key: String,
                    teamId: Int,
                    userId: Int,
                    template: String)

  case class TookanResponse(message: String,
                            status: Int)

  object TookanResponse {
    implicit val tookanResponseFormat = Json.format[TookanResponse]
  }

  case class Metadata(label: String,
                      data: String)

  object Metadata {
    implicit val metadataFormat = Json.format[Metadata]

    val maker: String = "Maker"
    val model: String = "Model"
    val year: String = "Year"
    val color: String = "Color"
    val interior: String = "Interior"
    val licPlate: String = "Lic_Plate"
  }

  case class AppointmentTask(customerEmail: Option[String],
                             customerUserName: String,
                             customerPhone: String,
                             customerAddress: String,
                             latitude: Option[Double],
                             longitude: Option[Double],
                             jobDescription: String,
                             pickupDateTime: LocalDateTime,
                             deliveryDateTime: LocalDateTime,
                             timezone: Int,
                             metaData: Seq[Metadata],
                             hasPickUp: Boolean,
                             hasDelivery: Boolean,
                             layoutType: Int,
                             trackingLink: Boolean,
                             teamId: Int,
                             autoAssignment: Boolean,
                             fleetId: Option[String],
                             refImages: Seq[String],
                             apiKey: Option[String],
                             customFieldTemplate: Option[String])

  object AppointmentTask {

    val PST = 480

    def default(customerName: String,
                customerPhone: String,
                customerAddress: String,
                jobDescription: String,
                pickupDateTime: LocalDateTime,
                latitude: Option[Double],
                longitude: Option[Double],
                customerEmail: Option[String],
                metadata: Seq[Metadata] = Seq.empty[Metadata])(implicit config: Config) =
      AppointmentTask(customerEmail,
        customerName,
        customerPhone,
        customerAddress,
        latitude,
        longitude,
        jobDescription,
        pickupDateTime,
        pickupDateTime.plusHours(1L),
        PST,
        metadata,
        false,
        false,
        1,
        true,
        config.teamId,
        true,
        None,
        Seq.empty[String],
        Option(config.key),
        Option(config.template))

    val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")

    implicit val appointmentTaskWrites: Writes[AppointmentTask] =
      Writes((task: AppointmentTask) =>
        Json.obj("customer_email" -> task.customerEmail,
          "customer_username" -> task.customerUserName,
          "customer_phone" -> task.customerPhone,
          "customer_address" -> task.customerAddress,
          "latitude" -> task.latitude,
          "longitude" -> task.longitude,
          "job_description" -> task.jobDescription,
          "job_pickup_datetime" -> formatter.format(task.pickupDateTime),
          "job_delivery_datetime" -> formatter.format(task.deliveryDateTime),
          "timezone" -> task.timezone,
          "meta_data" -> Json.toJson(task.metaData),
          "has_pickup" -> task.hasPickUp.convert,
          "has_delivery" -> task.hasDelivery.convert,
          "layout_type" -> task.layoutType,
          "tracking_link" -> task.trackingLink.convert,
          "access_token" -> task.apiKey,
          "team_id" -> task.teamId,
          "auto_assignment" -> task.autoAssignment.convert,
          "fleet_id" -> task.fleetId,
          "ref_images" -> Json.toJson(task.refImages),
          "custom_field_template" -> task.customFieldTemplate))

  }

  case class AppointmentResponse(jobId: Long,
                                 customerName: String,
                                 customerAddress: String,
                                 jobToken: String)

  object AppointmentResponse {
    implicit val taskDtoReads: Reads[AppointmentResponse] = (
      (__ \ "job_id").read[Long] and
        (__ \ "customer_name").read[String] and
        (__ \ "customer_address").read[String] and
        (__ \ "job_token").read[String]
      ) (AppointmentResponse.apply _)
    implicit val taskDtoWrites: Writes[AppointmentResponse] = Json.writes[AppointmentResponse]
  }

  case class DeleteTaskDto(accessToken: String,
                           jobId: Long)

  object DeleteTaskDto {

    def default(jobId: Long)(implicit config: Config) = new DeleteTaskDto(config.key, jobId)

    implicit val deleteTaskWrites: Format[DeleteTaskDto] = Json.format[DeleteTaskDto]
  }

  case class Fields(images: List[String])

  object Fields {
    implicit val fbTokenDtoReads: Reads[Fields] = (__ \ 'ref_images).read[List[String]].map(Fields.apply)
  }

  case class TaskAction(actionType: String,
                        description: String) {

    def isImageAction = actionType.equals("image_added")
  }

  object TaskAction {
    implicit val taskActionReads: Reads[TaskAction] = (
      (__ \ "type").read[String] and
        (__ \ "description").read[String]
      ) (TaskAction.apply _)
  }


  case class AppointmentDetails(jobId: Long,
                                fleetId: Option[Long],
                                jobStatus: Int,
                                pickupDatetime: String,
                                fields: Fields,
                                taskHistory: Seq[TaskAction],
                                address: String,
                                pickupPhone: String,
                                customerPhone: String) {

    def getDate = {
      val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a")
      LocalDateTime.parse(pickupDatetime.toUpperCase, formatter)
    }
  }


  object AppointmentDetails {
    implicit val appointmentDetailsReads: Reads[AppointmentDetails] = (
      (__ \ "job_id").read[Long] and
        (__ \ "fleet_id").readNullable[Long] and
        (__ \ "job_status").read[Int] and
        (__ \ "job_pickup_datetime").read[String] and
        (__ \ "fields").read[Fields] and
        (__ \ "task_history").read[Seq[TaskAction]] and
        (__ \ "job_address").read[String] and
        (__ \ "job_pickup_phone").read[String] and
        (__ \ "customer_phone").read[String]
      ) (AppointmentDetails.apply _)
  }

  case class Agent(fleetId: Long,
                   image: String,
                   name: String,
                   phone: String)

  object Agent {
    implicit val agentReads: Reads[Agent] = (
      (__ \ "fleet_id").read[Long] and
        (__ \ "fleet_image").read[String] and
        (__ \ "username").read[String] and
        (__ \ "phone").read[String]
      ) (Agent.apply _)
  }

  case class Team(teamId: Long,
                  teamName: String)

  object Team {
    implicit val teamReads: Reads[Team] = (
      (__ \ "team_id").read[Long] and
        (__ \ "team_name").read[String]
      ) (Team.apply _)
  }

  case class Coordinates(latitude: String,
                         longitude: String)

  object Coordinates {
    implicit val coordinatesReads: Reads[Coordinates] = (
      (__ \ "latitude").read[String] and
        (__ \ "longitude").read[String]
      ) (Coordinates.apply _)

    implicit val coordinatesWrites: Writes[Coordinates] = Writes((coordinates: Coordinates) =>
      Json.obj(
        "latitude" -> coordinates.latitude,
        "longitude" -> coordinates.longitude
      ))
  }

  implicit class BooleanEx(value: Boolean) {
    def convert = value match {
      case false => "0"
      case _ => "1"
    }
  }

}
