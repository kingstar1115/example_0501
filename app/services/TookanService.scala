package services

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.{Inject, Singleton}

import play.api.Configuration
import play.api.libs.functional.syntax._
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
    configuration.getInt("tookan.teamId").get)

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
    buildRequest(CREATE_TASK)
      .post(Json.toJson(task))
      .map(response => response.convert[AppointmentResponse])
  }

  def deleteTask(taskId: Long) = {
    buildRequest(DELETE_TASK)
      .post(Json.toJson(DeleteTaskDto.default(taskId)))
      .map(response => response.getResponse)
  }

  private def buildRequest(path: String) = {
    ws.url(buildUrl(path))
      .withRequestTimeout(10000L)
      .withHeaders((HeaderNames.CONTENT_TYPE, MimeTypes.JSON))
  }

  private def buildUrl(path: String) = BASE_URL.concat("/").concat(path)

  implicit class WSResponseEx(wsResponse: WSResponse) {

    val jsonBody = wsResponse.json

    def getResponse: TookanResponse = jsonBody.as[TookanResponse]

    def convert[T](implicit reads: Reads[T]): Either[TookanResponse, T] = {
      val response = getResponse
      response.status match {
        case 200 => Right((jsonBody \ "data").as[T])
        case _ => Left(response)
      }
    }
  }

}

object TookanService {

  val CREATE_TASK = "create_task"
  val DELETE_TASK = "delete_job"

  case class Config(key: String,
                    teamId: Int)

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
        false,
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

  implicit class BooleanEx(value: Boolean) {
    def convert = value match {
      case false => "0"
      case _ => "1"
    }
  }

}
