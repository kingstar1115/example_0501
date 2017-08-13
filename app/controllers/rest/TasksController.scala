package controllers.rest

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import commons.enums.{TaskStatuses, ValidationError => VError}
import controllers.rest.TasksController._
import controllers.rest.VehiclesController._
import controllers.rest.base._
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

  implicit val agentDtoFormat: Format[AgentDto] = Json.format[AgentDto]
  implicit val taskListDtoFormat: Format[TaskListDto] = Json.format[TaskListDto]
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
      val pendingTask = resultSet.headOption.map(row => convertToListDto(row))
      ok(pendingTask)
    }
  }

  def getPendingTasks(version: String) = authorized.async { request =>
    taskService.pendingTasks(request.token.get.userInfo.id).map { resultSet =>
      val pendingTasks = resultSet.map(row => convertToListDto(row))
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
          error.errorType
            .map(errorType => badRequest(error.message, errorType))
            .getOrElse(badRequest(error.message))
      }
    }
  }

  def listTasks(version: String, offset: Int, limit: Int) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val inStatuses = request.queryString.get("status").map(_.map(_.toInt).toSet)
    val notInStatuses = request.queryString.get("ignore").map(_.map(_.toInt).toSet)
    val submitted = request.getQueryString("submitted").map(_.toBoolean)

    val baseQuery = for {
      ((task, agent), vehicle) <- Tasks joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id)
      if task.userId === userId
    } yield (task, agent, vehicle)
    val filteredByInStatus = inStatuses.map(s => baseQuery.filter(_._1.jobStatus.inSet(s))).getOrElse(baseQuery)
    val filteredByNotInStatus = notInStatuses.map(s => filteredByInStatus.filterNot(_._1.jobStatus.inSet(s))).getOrElse(filteredByInStatus)
    val listQuery = submitted.map(s => filteredByInStatus.filter(_._1.submitted === s)).getOrElse(filteredByNotInStatus)

    db.run(listQuery.length.result zip listQuery.sortBy(_._1.createdDate.desc).take(limit).drop(offset).result)
      .map { resultSet =>
        val jobs = resultSet._2.map(row => convertToListDto(row)).toList
        ok(ListResponse(jobs, limit, offset, resultSet._1))
      }
  }

  def getTask(version: String, id: Long) = authorized.async { request =>
    val selectQuery = for {
      (((task, agent), vehicle), paymentDetails) <- Tasks joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id) join PaymentDetails on (_._1._1.id === _.taskId)
      if task.userId === request.token.get.userInfo.id && task.jobId === id
    } yield (task, agent, vehicle, paymentDetails)
    db.run(selectQuery.result.headOption).map(_.map { row =>
      ok(convertToDetailsDto(row))
    }.getOrElse(notFound))
  }

  def onTaskUpdate(version: String) = Action { implicit request =>
    val formData = Form(mapping(
      "job_id" -> Forms.longNumber,
      "job_status" -> Forms.number
    )(TaskHook.apply)(TaskHook.unapply)).bindFromRequest().get
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
    val selectQuery = for {
      (((task, agent), vehicle), paymentDetails) <- Tasks joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id) join PaymentDetails on (_._1._1.id === _.taskId)
      if task.userId === request.token.get.userInfo.id && task.jobStatus.inSet(TaskStatuses.activeStatuses)
    } yield (task, agent, vehicle, paymentDetails)
    db.run(selectQuery.sortBy(_._1.scheduledTime.asc).result.headOption).map { rowOpt =>
      val activeTaskOpt = rowOpt.map(row => convertToDetailsDto(row))
      ok(activeTaskOpt)
    }
  }

  private def convertToListDto[D](row: (TasksRow, Option[AgentsRow], VehiclesRow)) = {
    convertInternal(row._2, row._3) { (agentDto, vehicleDto) =>
      val task = row._1
      TaskListDto(task.jobId, task.scheduledTime.toLocalDateTime, agentDto, getJobImages(task), vehicleDto,
        task.jobStatus, task.submitted)
    }
  }

  private def convertToDetailsDto(row: (TasksRow, Option[AgentsRow], VehiclesRow, PaymentDetailsRow)) = {
    convertInternal(row._2, row._3) { (agentDto, vehicleDto) =>
      val task = row._1
      val paymentDetails = row._4
      TaskDetailsDto(task.jobId, task.scheduledTime.toLocalDateTime, agentDto, getJobImages(task), vehicleDto, task.jobStatus,
        task.submitted, task.teamName, task.jobAddress, task.jobPickupPhone, task.customerPhone, paymentDetails.paymentMethod,
        task.hasInteriorCleaning, paymentDetails.price, task.latitude, task.longitude, paymentDetails.promotion, paymentDetails.tip)
    }
  }

  private def convertInternal[D](agentOpt: Option[AgentsRow], vehicleRow: VehiclesRow)(consumer: (Option[AgentDto], VehicleDto) => D) = {
    val agent = agentOpt.map(agent => AgentDto(agent.fleetId, agent.name, agent.fleetImage, agent.phone))
      .orElse(None)
    val vehicle = VehicleDto(Some(vehicleRow.id), vehicleRow.makerId, vehicleRow.makerNiceName, vehicleRow.modelId,
      vehicleRow.modelNiceName, vehicleRow.yearId, vehicleRow.year, Option(vehicleRow.color), vehicleRow.licPlate)
    consumer.apply(agent, vehicle)
  }

  private def getJobImages(task: TasksRow) = {
    task.images.map(_.split(";").filter(_.nonEmpty).toList).getOrElse(List.empty[String])
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
                             customerReview: CustomerReviewDto)

  case class TaskHook(jobId: Long,
                      jobStatus: Int)

}


