package services

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}

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
    configuration.getInt("tookan.userId").get)

  val BASE_URL = configuration.getString("tookan.url").get

  def createAppointment(customerName: String,
                        customerPhone: String,
                        customerAddress: String,
                        jobDescription: String,
                        pickupDateTime: LocalDateTime,
                        latitude: Option[Double],
                        longitude: Option[Double],
                        customerEmail: Option[String]): Future[Either[TookanResponse, AppointmentResponse]] = {
    val task = AppointmentTask.default(customerName,
      customerPhone,
      customerAddress,
      jobDescription,
      pickupDateTime,
      latitude,
      longitude,
      customerEmail)
    createAppointment(task)
  }

  def createAppointment(task: AppointmentTask): Future[Either[TookanResponse, AppointmentResponse]] = {
    val json = Json.toJson(task)
    buildRequest(CREATE_TASK)
      .post(json)
      .map(response => response.convert[AppointmentResponse])
  }

  def deleteTask(jobId: Long) = {
    buildRequest(DELETE_TASK)
      .post(Json.toJson(DeleteTaskDto.default(jobId)))
      .map(response => response.getResponse)
  }

  def getTask(jobId: Long): Future[Either[TookanResponse, AppointmentDetails]] = {
    buildRequest(TASK_DETAILS)
      .post(Json.obj(
        "access_token" -> config.key,
        "user_id" -> config.userId,
        "job_id" -> jobId.toString))
      .map { response =>
        response.asList[AppointmentDetails] match {
          case Right(list) =>
            Right(list.head)
          case Left(e) =>
            Left(e)
        }
      }
  }

  def listAgents: Future[Either[TookanResponse, List[Agent]]] = {
    buildRequest(LIST_AGENTS)
      .post(Json.obj("access_token" -> config.key))
      .map(response => response.asList[Agent])
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

  val CREATE_TASK = "create_task"
  val DELETE_TASK = "delete_job"
  val TASK_DETAILS = "view_task_profile"
  val LIST_AGENTS = "view_all_fleets_location"

  case class Config(key: String,
                    teamId: Int,
                    userId: Int)

  case class TookanResponse(message: String,
                            status: Int)

  object TookanResponse {
    implicit val tookanResponseFormat = Json.format[TookanResponse]
  }

  case class Metadata(label: String,
                      data: String)

  object Metadata {
    implicit val metadataFormat = Json.format[Metadata]
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
                             apiKey: Option[String])

  object AppointmentTask {

    val PST = 480

    def default(customerName: String,
                customerPhone: String,
                customerAddress: String,
                jobDescription: String,
                pickupDateTime: LocalDateTime,
                latitude: Option[Double],
                longitude: Option[Double],
                customerEmail: Option[String])(implicit config: Config) =
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
        Seq.empty[Metadata],
        false,
        false,
        1,
        true,
        config.teamId,
        false,
        None,
        Seq.empty[String],
        Some(config.key))

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
          "ref_images" -> Json.toJson(task.refImages)))

  }

  case class AppointmentResponse(jobId: Long,
                                 customerName: String,
                                 customerAddress: String,
                                 jobToken: String)

  object AppointmentResponse {
    implicit val taskDtoReads: Reads[AppointmentResponse] = (
      (JsPath \ "job_id").read[Long] and
        (JsPath \ "customer_name").read[String] and
        (JsPath \ "customer_address").read[String] and
        (JsPath \ "job_token").read[String]
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

  case class AppointmentDetails(jobId: Long,
                                fleetId: Option[Long],
                                jobStatus: Int,
                                pickupDatetime: String,
                                fields: Fields) {

    def getDate = {
      val formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy hh:mm a")
      LocalDateTime.parse(pickupDatetime.toUpperCase, formatter)
    }
  }


  object AppointmentDetails {

    implicit val appointmentDetailsReads: Reads[AppointmentDetails] = (
      (JsPath \ "job_id").read[Long] and
        (JsPath \ "fleet_id").readNullable[Long] and
        (JsPath \ "job_status").read[Int] and
        (JsPath \ "job_pickup_datetime").read[String] and
        (JsPath \ "fields").read[Fields]
      ) (AppointmentDetails.apply _)
  }

  case class Agent(fleetId: Long,
                   image: String,
                   name: String)

  object Agent {

    implicit val agentReads: Reads[Agent] = (
      (JsPath \ "fleet_id").read[Long] and
        (JsPath \ "fleet_image").read[String] and
        (JsPath \ "username").read[String]
      ) (Agent.apply _)
  }

  implicit class BooleanEx(value: Boolean) {
    def convert = value match {
      case false => "0"
      case _ => "1"
    }
  }

}
