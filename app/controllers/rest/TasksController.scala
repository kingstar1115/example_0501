package controllers.rest

import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import commons.enums.{TaskStatuses, ValidationError => VError}
import controllers.rest.TasksController._
import controllers.rest.VehiclesController._
import controllers.rest.base._
import javax.inject.Inject
import models.Tables._
import play.api.data.Forms.{email => _, _}
import play.api.data.validation.ValidationError
import play.api.data.{Form, Forms}
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{Reads, _}
import play.api.mvc.{Action, BodyParsers}
import play.api.{Configuration, Logger}
import security.TokenStorage
import services.internal.bookings.BookingService
import services.internal.settings.SettingsService
import services.internal.tasks.TasksService
import services.internal.tasks.TasksService._
import services.internal.users.UsersService
import services.{StripeService, _}
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future


//noinspection TypeAnnotation
class TasksController @Inject()(val tokenStorage: TokenStorage,
                                dbConfigProvider: DatabaseConfigProvider,
                                tookanService: TookanService,
                                stripeService: StripeService,
                                config: Configuration,
                                settingsService: SettingsService,
                                taskService: TasksService,
                                usersService: UsersService,
                                bookingService: BookingService) extends BaseController {

  private val logger = Logger(this.getClass)

  implicit val agentDtoFormat: Format[AgentDto] = Json.format[AgentDto]
  implicit val taskListDtoFormat: Format[TaskListDto] = Json.format[TaskListDto]
  implicit val taskServiceDtoFormat: Format[TaskServiceDto] = Json.format[TaskServiceDto]
  implicit val taskDetailsDtoFormat: Format[TaskDetailsDto] = Json.format[TaskDetailsDto]
  implicit val tipDtoFormat: Format[TipDto] = Json.format[TipDto]
  implicit val customerReviewDtoFormat: Format[CustomerReviewDto] = Json.format[CustomerReviewDto]
  implicit val completeTaskDtoFormat: Format[CompleteTaskDto] = Json.format[CompleteTaskDto]

  val db = dbConfigProvider.get.db

  def createCustomerTask(version: String) = authorized.async(BodyParsers.parse.json) { implicit request =>
    def createTask(vehicleId: Int)(implicit appointmentTask: PaidAppointmentTask) = {
      taskService.createTaskForCustomer(request.token.get.userInfo.id, vehicleId) map {
        case Left(error) => badRequest(error.message)
        case Right(tookanTask) => ok(tookanTask)
      }
    }

    version match {
      case "v3" =>
        processRequestF[CustomerTaskWithServicesDto](request.body) { dto =>
          implicit val appointmentTask = PaidCustomerTaskWithAccommodations(dto.description, dto.address, dto.latitude, dto.longitude, dto.dateTime,
            CustomerPaymentInformation(dto.paymentDetails.token, dto.paymentDetails.cardId), dto.paymentDetails.promotion, dto.service.id, dto.service.extras)
          createTask(dto.vehicleId)
        }
      case "v2" =>
        processRequestF[CustomerTaskDto](request.body) { dto =>
          implicit val appointmentTask = PaidCustomerTaskWithInteriorCleaning(dto.description, dto.address, dto.latitude, dto.longitude, dto.dateTime,
            CustomerPaymentInformation(dto.paymentDetails.token, dto.paymentDetails.cardId), dto.paymentDetails.promotion, dto.paymentDetails.hasInteriorCleaning)
          createTask(dto.vehicleId)
        }
      case _ =>
        processRequestF[TaskDto](request.body) { dto =>
          implicit val appointmentTask = PaidCustomerTaskWithInteriorCleaning(dto.description, dto.pickupAddress, dto.pickupLatitude, dto.pickupLongitude, dto.pickupDateTime,
            CustomerPaymentInformation(dto.token, dto.cardId), dto.promotion, dto.hasInteriorCleaning)
          createTask(dto.vehicleId)
        }
    }
  }

  def createAnonymousTask(version: String) = Action.async(BodyParsers.parse.json) { request =>
    def createTask(user: User, vehicle: Vehicle)(implicit appointmentTask: PaidAppointmentTask) = {
      taskService.createTaskForAnonymous(user, vehicle) map {
        case Left(error) => badRequest(error.message)
        case Right(tookanTask) => ok(tookanTask)
      }
    }

    version match {
      case "v3" =>
        processRequestF[AnonymousTaskWithServicesDto](request.body) { dto =>
          implicit val appointmentTask = PaidAnonymousTaskWithAccommodations(dto.description, dto.address, dto.latitude, dto.longitude,
            dto.dateTime, AnonymousPaymentInformation(dto.paymentDetails.token), dto.paymentDetails.promotion, dto.paymentDetails.tip,
            dto.serviceDto.id, dto.serviceDto.extras)
          createTask(dto.userDto.mapToUser, dto.vehicleDto.mapToVehicle)
        }
      case _ =>
        processRequestF[AnonymousTaskDto](request.body) { dto =>
          implicit val appointmentTask = PaidAnonymousTaskWithInteriorCleaning(dto.description, dto.address, dto.latitude, dto.longitude, dto.dateTime,
            AnonymousPaymentInformation(dto.paymentDetails.token), dto.paymentDetails.promotion, dto.paymentDetails.hasInteriorCleaning)
          createTask(dto.user, dto.vehicle.mapToVehicle)
        }
    }
  }

  def createPartnershipTask(version: String) = Action.async(BodyParsers.parse.json) { request =>
    def createPartnershipTask(user: User, vehicle: Vehicle)(implicit appointmentTask: AppointmentTask) = {
      taskService.createPartnershipTask(user, vehicle) map {
        case Left(error) => badRequest(error.message)
        case Right(tookanTask) => ok(tookanTask)
      }
    }

    version match {
      case "v3" =>
        processRequestF[PartnershipTaskWithServiceDto](request.body) { dto =>
          createPartnershipTask(dto.userDto.mapToUser, dto.vehicleDto.mapToVehicle)(dto.mapToAppointmentTask)
        }
      case _ =>
        processRequestF[PartnershipTaskDto](request.body) { dto =>
          createPartnershipTask(dto.user, dto.vehicleDto.mapToVehicle)(dto.mapToAppointmentTask)
        }
    }
  }

  def cancelTask(version: String, id: Long) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val selectQuery = for {
      (task, paymentDetails) <- Tasks join PaymentDetails on (_.id === _.taskId)
      if task.userId === userId && task.jobStatus.inSet(TaskStatuses.cancelableStatuses) && task.jobId === id
    } yield (task, paymentDetails)

    db.run(selectQuery.result.headOption).flatMap {
      case Some((task, paymentDetails)) =>
        val refundResult = paymentDetails.chargeId.map { chargeId =>
          Logger.debug(s"Refunding charge $chargeId for customer ${request.token.get.userInfo.email}")
          stripeService.refund(chargeId).map {
            case Left(_) =>
              Logger.debug(s"Can't refund money for task $id")
              Option(chargeId)
            case Right(_) =>
              Logger.debug(s"Refunded money for task with $id id")
              Option.empty
          }
        }.getOrElse(Future.successful(Option.empty))

        refundResult.flatMap { chargeOpt =>
          tookanService.updateTaskStatus(id, TaskStatuses.Cancel)
          val updateAction = DBIO.seq(
            Tasks.filter(_.id === task.id).map(_.jobStatus).update(TaskStatuses.Cancel.code),
            PaymentDetails.filter(_.taskId === task.id).map(_.chargeId).update(chargeOpt),
            DBIO.from(bookingService.findTimeSlot(task.timeSlotId)
              .map(_.get)
              .flatMap(r => bookingService.releaseBooking(r)))
          ).transactionally
          db.run(updateAction).map(_ => success)
        }
      case None =>
        Future.successful(badRequest(s"Can't cancel task with $id id"))
    }
  }

  def getPendingTask = authorized.async { request =>
    taskService.pendingTasks(request.token.get.userInfo.id).map { resultSet =>
      val pendingTask = resultSet.headOption.map(TaskListDto.convert)
      ok(pendingTask)
    }
  }

  def getPendingTasks(version: String) = authorized.async { request =>
    taskService.pendingTasks(request.token.get.userInfo.id).map { resultSet =>
      val pendingTasks = resultSet.map(TaskListDto.convert)
      ok(pendingTasks)
    }
  }

  def completeTask(version: String) = authorized.async(BodyParsers.parse.json) { request =>
    processRequestF[CompleteTaskDto](request.body) { dto =>
      val userId = request.token.get.userInfo.id
      taskService.completeTask(dto, userId).map {
        case Right(_) =>
          success
        case Left(error) =>
          Logger.warn(s"[${LocalDateTime.now()}] - Failed to complete task for user($userId, ${request.token}). Error: $error " +
            s"Request body: \n\t $dto")
          error.errorType
            .map(errorType => badRequest(error.message, errorType))
            .getOrElse(badRequest(error.message))
      }
    }
  }

  def listTasks(version: String, offset: Int, limit: Int, status: Set[Int], ignore: Set[Int]) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val submitted = request.getQueryString("submitted").map(_.toBoolean)

    val baseQuery = for {
      ((task, agent), vehicle) <- Tasks joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if task.userId === userId
    } yield (task, agent, vehicle)
    val filteredByInStatus = if (status.nonEmpty) baseQuery.filter(_._1.jobStatus.inSet(status)) else baseQuery
    val filteredByNotInStatus = if (ignore.nonEmpty) filteredByInStatus.filterNot(_._1.jobStatus.inSet(ignore)) else filteredByInStatus
    val listQuery = submitted.map(s => filteredByInStatus.filter(_._1.submitted === s)).getOrElse(filteredByNotInStatus)

    db.run(listQuery.length.result zip listQuery.sortBy(_._1.createdDate.desc).take(limit).drop(offset).result)
      .map { resultSet =>
        val jobs = resultSet._2.map(TaskListDto.convert).toList
        ok(ListResponse(jobs, limit, offset, resultSet._1))
      }
  }

  def getTask(version: String, id: Long) = authorized.async { request =>
    taskService.getTask(id, request.token.get.userInfo.id)
      .map(_.map(taskDetails => ok(taskDetails)).getOrElse(notFound))
  }

  def onTaskUpdate(version: String) = Action { implicit request =>
    val formData = Form(mapping(
      "job_id" -> Forms.longNumber,
      "job_status" -> Forms.number
    )(TaskHook.apply)(TaskHook.unapply)).bindFromRequest().get
    logger.info(s"Request to refresh task: $formData")
    taskService.refreshTask(formData.jobId)
    NoContent
  }

  def getAgentCoordinates(version: String, fleetId: Long) = authorized.async { request =>
    tookanService.getAgentCoordinates(fleetId).map {
      case Right(coordinates) =>
        ok(coordinates)
      case Left(e) =>
        badRequest(e.message)
    }
  }

  def getActiveTask(version: String) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val taskQuery = (
      for {
        ((((task, agent), vehicle), paymentDetails)) <- Tasks
          .joinLeft(Agents).on(_.agentId === _.id)
          .join(Vehicles).on(_._1.vehicleId === _.id)
          .join(PaymentDetails).on(_._1._1.id === _.taskId)
        if task.userId === userId && task.jobStatus.inSet(TaskStatuses.activeStatuses)
      } yield (task, agent, vehicle, paymentDetails)
      ).sortBy(_._1.scheduledTime.asc)
    val servicesQuery = for {
      (taskServices) <- TaskServices
      if taskServices.taskId in Tasks
        .filter(task => task.userId === userId && task.jobStatus.inSet(TaskStatuses.activeStatuses))
        .sortBy(_.scheduledTime.asc)
        .take(1)
        .map(_.id)
    } yield taskServices

    db.run(taskQuery.result.headOption.zip(servicesQuery.result)).map { result =>
      val activeTaskDto = result._1
        .map(row => TaskDetailsDto.convert((row._1, row._2, row._3, row._4, null), result._2))
      ok(activeTaskDto)
    }
  }
}

object TasksController {

  case class AgentDto(fleetId: Long,
                      name: String,
                      picture: String,
                      phone: String,
                      rating: BigDecimal)

  object AgentDto {
    def fromAgent(agent: AgentsRow): AgentDto = {
      AgentDto(agent.fleetId, agent.name, agent.fleetImage, agent.phone, agent.avrCustomerRating)
    }
  }

  case class TaskListDto(jobId: Long,
                         scheduledDateTime: LocalDateTime,
                         agent: Option[AgentDto],
                         images: List[String],
                         vehicle: VehicleDto,
                         status: Int,
                         submitted: Boolean)

  object TaskListDto {

    type ListTaskDetails = (TasksRow, Option[AgentsRow], VehiclesRow)

    def convert(taskDetails: ListTaskDetails): TaskListDto = {
      val agentDto = taskDetails._2.map(AgentDto.fromAgent)
      val vehicleDto = VehicleDto.convert(taskDetails._3)

      TaskListDto(taskDetails._1.jobId, taskDetails._1.scheduledTime.toLocalDateTime, agentDto,
        TaskDetailsDto.getJobImages(taskDetails._1), vehicleDto, taskDetails._1.jobStatus, taskDetails._1.submitted)
    }
  }

  case class TaskServiceDto(name: String, price: Int, primary: Boolean)

  object TaskServiceDto {
    def fromService(serviceWithIndex: (TaskServicesRow, Int)): TaskServiceDto = {
      TaskServiceDto(serviceWithIndex._1.name, serviceWithIndex._1.price,
        serviceWithIndex._2 == 0)
    }
  }

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
                            tip: Int,
                            services: Seq[TaskServiceDto] = Seq.empty,
                            rating: Option[Int])

  object TaskDetailsDto {

    type TaskDetails = (TasksRow, Option[AgentsRow], VehiclesRow, PaymentDetailsRow, TaskServicesRow)

    implicit def ordered: Ordering[Timestamp] = new Ordering[Timestamp] {
      def compare(x: Timestamp, y: Timestamp): Int = x compareTo y
    }

    def convert(taskDetails: TaskDetails, services: Seq[TaskServicesRow]): TaskDetailsDto = {
      val servicesDto = convertServices(services)
      val agentDto = taskDetails._2.map(AgentDto.fromAgent)
      val vehicleDto = VehicleDto.convert(taskDetails._3)

      TaskDetailsDto(taskDetails._1.jobId, taskDetails._1.scheduledTime.toLocalDateTime, agentDto, getJobImages(taskDetails._1),
        vehicleDto, taskDetails._1.jobStatus, taskDetails._1.submitted, taskDetails._1.teamName, taskDetails._1.jobAddress,
        taskDetails._1.jobPickupPhone, taskDetails._1.customerPhone, taskDetails._4.paymentMethod,
        taskDetails._1.hasInteriorCleaning, services.head.price, taskDetails._1.latitude, taskDetails._1.longitude,
        taskDetails._4.promotion, taskDetails._4.tip, servicesDto, taskDetails._1.rating)
    }

    private def convertServices(services: Seq[TaskServicesRow]): Seq[TaskServiceDto] = {
      services.sortBy(service => service.createdDate)
        .zipWithIndex
        .map(TaskServiceDto.fromService)
    }

    def getJobImages(task: TasksRow): List[String] = {
      task.images.map(_.split(";").filter(_.nonEmpty).toList).getOrElse(List.empty[String])
    }
  }

  //---------------------------------------Common-----------------------------------------------------------------------

  trait UserInformation {
    def name: String

    def phone: String

    def email: Option[String]

    def user: User = User(name, phone, email)
  }

  case class UserDto(name: String, phone: String, email: Option[String]) {
    def mapToUser: User = User.tupled(UserDto.unapply(this).get)
  }

  implicit val userDtoFormat: Format[UserDto] = Json.format[UserDto]

  case class VehicleDetailsDto(maker: String,
                               model: String,
                               year: Int,
                               color: String,
                               licPlate: Option[String]) {
    def mapToVehicle: Vehicle = Vehicle.tupled(VehicleDetailsDto.unapply(this).get)
  }

  implicit val vehicleDetailsFormat: Format[VehicleDetailsDto] = Json.format[VehicleDetailsDto]


  case class ServiceDto(id: Int, extras: Set[Int])

  implicit val serviceDtoFormat: Format[ServiceDto] = Json.format[ServiceDto]

  val dateTimeReads: Reads[LocalDateTime] = localDateTimeReads(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"))

  //--------------------------------------------------------------------------------------------------------------------

  //-------------------------------Registered Customer------------------------------------------------------------------
  case class CustomerPaymentDetailsWithInteriorCleaning(promotion: Option[Int],
                                                        hasInteriorCleaning: Boolean,
                                                        token: Option[String],
                                                        cardId: Option[String])

  implicit val customerPaymentDetailsWithInteriorCleaningReads: Reads[CustomerPaymentDetailsWithInteriorCleaning] = (
    (__ \ "promotion").readNullable[Int] and
      (__ \ "hasInteriorCleaning").read[Boolean] and
      (__ \ "token").readNullable[String] and
      (__ \ "cardId").readNullable[String]
    ) (CustomerPaymentDetailsWithInteriorCleaning.apply _)
    .filter(ValidationError("Token or card id must be provided"))(dto => dto.token.isDefined || dto.cardId.isDefined)

  case class CustomerTaskDto(description: String, address: String, latitude: Double, longitude: Double, dateTime: LocalDateTime,
                             vehicleId: Int, paymentDetails: CustomerPaymentDetailsWithInteriorCleaning)

  implicit val customerTaskReads: Reads[CustomerTaskDto] = (
    (__ \ "description").read[String] and
      (__ \ "address").read[String] and
      (__ \ "latitude").read[Double] and
      (__ \ "longitude").read[Double] and
      (__ \ "dateTime").read[LocalDateTime](dateTimeReads) and
      (__ \ "vehicleId").read[Int] and
      (__ \ "paymentDetails").read[CustomerPaymentDetailsWithInteriorCleaning]
    ) (CustomerTaskDto.apply _)

  case class CustomerPaymentDetails(promotion: Option[Int],
                                    token: Option[String],
                                    cardId: Option[String])

  implicit val customerPaymentDetailsReads: Reads[CustomerPaymentDetails] = (
    (__ \ "promotion").readNullable[Int] and
      (__ \ "token").readNullable[String] and
      (__ \ "cardId").readNullable[String]
    ) (CustomerPaymentDetails.apply _)
    .filter(ValidationError("Token or card id must be provided"))(dto => dto.token.isDefined || dto.cardId.isDefined)

  case class CustomerTaskWithServicesDto(description: String, address: String, latitude: Double, longitude: Double,
                                         dateTime: LocalDateTime, vehicleId: Int, service: ServiceDto,
                                         paymentDetails: CustomerPaymentDetails)

  implicit val customerTaskWithServicesReads: Reads[CustomerTaskWithServicesDto] = (
    (__ \ "description").read[String] and
      (__ \ "address").read[String] and
      (__ \ "latitude").read[Double] and
      (__ \ "longitude").read[Double] and
      (__ \ "dateTime").read[LocalDateTime](dateTimeReads) and
      (__ \ "vehicleId").read[Int] and
      (__ \ "service").read[ServiceDto] and
      (__ \ "paymentDetails").read[CustomerPaymentDetails]
    ) (CustomerTaskWithServicesDto.apply _)

  //--------------------------------------------------------------------------------------------------------------------

  //--------------------------------Anonymous Customer------------------------------------------------------------------
  case class AnonymousPaymentDetailsWithInterior(token: String,
                                                 promotion: Option[Int],
                                                 hasInteriorCleaning: Boolean)

  implicit val anonymousPaymentDetailsWithInteriorFormat: Format[AnonymousPaymentDetailsWithInterior] = Json.format[AnonymousPaymentDetailsWithInterior]

  case class AnonymousTaskDto(description: String, name: String, email: Option[String], phone: String, address: String,
                              latitude: Double, longitude: Double, dateTime: LocalDateTime, vehicle: VehicleDetailsDto,
                              paymentDetails: AnonymousPaymentDetailsWithInterior) extends UserInformation

  implicit val anonymousTaskReads: Reads[AnonymousTaskDto] = (
    (__ \ "description").read[String] and
      (__ \ "name").read[String] and
      (__ \ "email").readNullable[String] and
      (__ \ "phone").read[String] and
      (__ \ "address").read[String] and
      (__ \ "latitude").read[Double] and
      (__ \ "longitude").read[Double] and
      (__ \ "dateTime").read[LocalDateTime](dateTimeReads) and
      (__ \ "vehicle").read[VehicleDetailsDto] and
      (__ \ "paymentDetails").read[AnonymousPaymentDetailsWithInterior]
    ) (AnonymousTaskDto.apply _)

  case class AnonymousPaymentDetails(token: String, promotion: Option[Int], tip: Option[Int])

  implicit val anonymousPaymentDetailsFormat: Format[AnonymousPaymentDetails] = Json.format[AnonymousPaymentDetails]

  case class AnonymousTaskWithServicesDto(description: String, userDto: UserDto, address: String, latitude: Double,
                                          longitude: Double, dateTime: LocalDateTime, vehicleDto: VehicleDetailsDto,
                                          serviceDto: ServiceDto, paymentDetails: AnonymousPaymentDetails)

  implicit val anonymousTaskWithServicesReads: Reads[AnonymousTaskWithServicesDto] = (
    (__ \ "description").read[String] and
      (__ \ "user").read[UserDto] and
      (__ \ "address").read[String] and
      (__ \ "latitude").read[Double] and
      (__ \ "longitude").read[Double] and
      (__ \ "dateTime").read[LocalDateTime](dateTimeReads) and
      (__ \ "vehicle").read[VehicleDetailsDto] and
      (__ \ "service").read[ServiceDto] and
      (__ \ "paymentDetails").read[AnonymousPaymentDetails]
    ) (AnonymousTaskWithServicesDto.apply _)

  //--------------------------------------------------------------------------------------------------------------------

  //--------------------------------Partnership Customer------------------------------------------------------------------

  case class PartnershipPaymentDetails(hasInteriorCleaning: Boolean)

  implicit val partnershipPaymentDetailsFormat: Format[PartnershipPaymentDetails] = Json.format[PartnershipPaymentDetails]

  case class PartnershipTaskDto(description: String, name: String, email: Option[String], phone: String, address: String,
                                latitude: Double, longitude: Double, dateTime: LocalDateTime, vehicleDto: VehicleDetailsDto,
                                paymentDetails: PartnershipPaymentDetails) extends UserInformation {
    def mapToAppointmentTask: PartnershipTaskWithInteriorCleaning = PartnershipTaskWithInteriorCleaning(description,
      address, latitude, longitude, dateTime, paymentDetails.hasInteriorCleaning)
  }

  implicit val partnershipTaskReads: Reads[PartnershipTaskDto] = (
    (__ \ "description").read[String] and
      (__ \ "name").read[String] and
      (__ \ "email").readNullable[String] and
      (__ \ "phone").read[String] and
      (__ \ "address").read[String] and
      (__ \ "latitude").read[Double] and
      (__ \ "longitude").read[Double] and
      (__ \ "dateTime").read[LocalDateTime](dateTimeReads) and
      (__ \ "vehicle").read[VehicleDetailsDto] and
      (__ \ "paymentDetails").read[PartnershipPaymentDetails]
    ) (PartnershipTaskDto.apply _)

  case class PartnershipTaskWithServiceDto(description: String, address: String, latitude: Double, longitude: Double,
                                           dateTime: LocalDateTime, userDto: UserDto, vehicleDto: VehicleDetailsDto, serviceDto: ServiceDto) {
    def mapToAppointmentTask: PartnershipTaskWithAccommodations = PartnershipTaskWithAccommodations(description, address,
      latitude, longitude, dateTime, serviceDto.id, serviceDto.extras)
  }

  implicit val partnershipTaskWithService: Reads[PartnershipTaskWithServiceDto] = (
    (__ \ "description").read[String] and
      (__ \ "address").read[String] and
      (__ \ "latitude").read[Double] and
      (__ \ "longitude").read[Double] and
      (__ \ "dateTime").read[LocalDateTime](dateTimeReads) and
      (__ \ "user").read[UserDto] and
      (__ \ "vehicle").read[VehicleDetailsDto] and
      (__ \ "service").read[ServiceDto]
    ) (PartnershipTaskWithServiceDto.apply _)

  //--------------------------------------------------------------------------------------------------------------------

  case class TaskDto(token: Option[String],
                     cardId: Option[String],
                     description: String,
                     name: String,
                     email: Option[String],
                     phone: String,
                     pickupAddress: String,
                     pickupLatitude: Double,
                     pickupLongitude: Double,
                     pickupDateTime: LocalDateTime,
                     hasInteriorCleaning: Boolean,
                     vehicleId: Int,
                     promotion: Option[Int]) extends UserInformation

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

  case class TipDto(amount: Int,
                    cardId: Option[String],
                    token: Option[String])

  case class CustomerReviewDto(rating: Int, comment: Option[String])

  case class CompleteTaskDto(jobId: Long,
                             tip: Option[TipDto],
                             customerReview: Option[CustomerReviewDto])

  case class TaskHook(jobId: Long,
                      jobStatus: Int)

}


