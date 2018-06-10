package services

import java.time.{LocalDate, LocalDateTime}
import java.time.format.DateTimeFormatter

import javax.inject.{Inject, Singleton}
import commons.enums.TaskStatuses.TaskStatus
import play.api.{Configuration, Logger}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.mvc.Http.{HeaderNames, MimeTypes}
import services.TookanService._

import scala.concurrent.Future

@Singleton
class TookanService @Inject()(ws: WSClient,
                              configuration: Configuration) {

  private val logger = Logger(this.getClass)

  implicit val config: Config = Config(
    configuration.getString("tookan.key").get,
    configuration.getString("tookan.v2Key").get,
    configuration.getInt("tookan.teamId").get,
    configuration.getInt("tookan.userId").get,
    configuration.getString("tookan.template").get)

  val BaseUrl: String = configuration.getString("tookan.url").get

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
    buildJsonRequest(CreateTask, V2)
      .post(Json.toJson(task))
      .map(response => response.convert[AppointmentResponse]())
  }

  def deleteTask(jobId: Long): Future[TookanResponse] = {
    buildJsonRequest(DeleteTask, V2)
      .post(Json.obj(
        "api_key" -> config.v2Key,
        "job_id" -> jobId.toString
      ))
      .map(response => response.getResponse)
  }

  def getTask(jobId: Long): Future[Either[TookanResponse, AppointmentDetails]] = {
    buildJsonRequest(TaskDetails, V2)
      .post(Json.obj(
        "api_key" -> config.v2Key,
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
    buildJsonRequest(TeamDetails)
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

  def getAgentCoordinates(fleetId: Long): Future[Either[TookanResponse, Coordinates]] = {
    buildJsonRequest(AgentCoordinates)
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

  def updateTaskStatus(taskId: Long, status: TaskStatus): Future[WSResponse] = {
    buildJsonRequest(UpdateTaskStatus, V2)
      .post(Json.obj(
        "api_key" -> config.v2Key,
        "job_id" -> taskId.toString,
        "job_status" -> status.code
      ))
  }

  private def buildJsonRequest(path: String, apiVersion: String = "") = {
    val url = BaseUrl
      .concat(apiVersion)
      .concat("/")
      .concat(path)
    ws.url(url)
      .withHeaders((HeaderNames.CONTENT_TYPE, MimeTypes.JSON))
  }

  def leaveCustomerReview(customerReview: CustomerReview): Future[WSResponse] = {
    val url = BaseUrl.concat("/").concat(CustomerRating)
    ws.url(url)
      .withHeaders((HeaderNames.CONTENT_TYPE, MimeTypes.FORM))
      .post(Map(
        "rating" -> Seq(customerReview.rating.toString),
        "customer_comment" -> Seq(customerReview.comment.getOrElse("")),
        "job_id" -> Seq(customerReview.jobHash)
      ))
  }

  def getAgent(id: Long): Future[Either[TookanResponse, Agent]] = {
    buildFormRequest(ListAgentsWithRating)
      .post(Map(
        "user_id" -> Seq(config.userId.toString),
        "team_id" -> Seq(config.teamId.toString),
        "access_token" -> Seq(config.key),
        "date" -> Seq(LocalDate.now().toString),
        "is_offline" -> Seq("1")
      ))
      .map(response => response.convert[Agent](__ \ "data" \ "teams" \\ "fleets" \\ id.toString))
  }

  private def buildFormRequest(path: String) = {
    ws.url(BaseUrl.concat("/").concat(path))
      .withHeaders((HeaderNames.CONTENT_TYPE, MimeTypes.FORM))
  }


  implicit class WSResponseEx(wsResponse: WSResponse) {

    val jsonBody: JsValue = wsResponse.json

    def getResponse: TookanResponse = jsonBody.as[TookanResponse](TookanResponse.tookanResponseFormat)

    def convert[T](path: JsPath = JsPath \ "data")(implicit reads: Reads[T]): Either[TookanResponse, T] = {
      val response = getResponse
      response.status match {
        case 200 =>
          path.asSingleJson(jsonBody).asOpt[T]
            .map(jsValue => Right(jsValue))
            .getOrElse({
              logger.warn(s"Failed to find path `$path` in ${Json.prettyPrint(jsonBody)}")
              Left(response)
            })
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
  val DeleteTask = "delete_task"
  val TaskDetails = "get_task_details"
  val TeamDetails = "view_team"
  val AgentCoordinates = "view_all_fleets"
  val UpdateTaskStatus = "update_task_status"
  val CustomerRating = "customer_rating"
  val ListAgentsWithRating = "getJobAndFleetDetails"

  val V2 = "/v2"

  case class Config(key: String,
                    v2Key: String,
                    teamId: Int,
                    userId: Int,
                    template: String)

  case class TookanResponse(message: String,
                            status: Int)

  object TookanResponse {
    implicit val tookanResponseFormat: Format[TookanResponse] = Json.format[TookanResponse]
  }

  case class Metadata(label: String,
                      data: String)

  object Metadata {
    implicit val metadataFormat: Format[Metadata] = Json.format[Metadata]

    val maker: String = "Maker"
    val model: String = "Model"
    val year: String = "Year"
    val color: String = "Color"
    val plate: String = "Plate"
    val services: String = "Services"
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
        hasPickUp = false,
        hasDelivery = false,
        1,
        trackingLink = true,
        config.teamId,
        autoAssignment = true,
        None,
        Seq.empty[String],
        Option(config.v2Key),
        Option(config.template))

    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")

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
          "api_key" -> task.apiKey,
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

  case class Fields(images: List[String])

  object Fields {
    implicit val fbTokenDtoReads: Reads[Fields] = (__ \ 'ref_images).read[List[String]].map(Fields.apply)
  }

  case class TaskAction(actionType: String,
                        description: String) {

    def isImageAction: Boolean = actionType.equals("image_added")
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
                                customerPhone: String,
                                jobHash: String) {

    def getDate: LocalDateTime = {
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
        (__ \ "customer_phone").read[String] and
        (__ \ "job_hash").read[String]
      ) (AppointmentDetails.apply _)
  }

  case class Agent(fleetId: Long,
                   image: String,
                   name: String,
                   phone: String,
                   avrCustomerRating: BigDecimal)

  object Agent {
    implicit val agentReads: Reads[Agent] = (
      (__ \ "fleet_id").read[Long] and
        (__ \ "fleet_image").read[String] and
        (__ \ "username").read[String] and
        (__ \ "phone").read[String] and
        (__ \ "avg_cust_rating").read[BigDecimal]
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

  case class CustomerReview(rating: Int,
                            comment: Option[String],
                            jobHash: String)

  implicit class BooleanEx(value: Boolean) {
    def convert: String = if (value) "1" else "0"
  }

}
