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
import services.internal.users.UsersService
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
                                taskService: TasksService,
                                usersService: UsersService) extends BaseController {

  implicit val agentDtoFormat: Format[AgentDto] = Json.format[AgentDto]
  implicit val taskListDtoFormat: Format[TaskListDto] = Json.format[TaskListDto]
  implicit val taskDetailsDtoFormat: Format[TaskDetailsDto] = Json.format[TaskDetailsDto]
  implicit val tipDtoFormat: Format[TipDto] = Json.format[TipDto]
  implicit val completeTaskDtoFormat: Format[CompleteTaskDto] = Json.format[CompleteTaskDto]

  val db = dbConfigProvider.get.db

  def createCustomerTask(version: String) = authorized.async(BodyParsers.parse.json) { implicit request =>
    def createTask(appointmentTask: PaidAppointmentTask, vehicleId: Int) = {
      taskService.createTaskForCustomer(appointmentTask, request.token.get.userInfo.id, vehicleId) map {
        case Left(error) => badRequest(error.message)
        case Right(tookanTask) => ok(tookanTask)
      }
    }

    version match {
      case "v3" =>
        processRequestF[CustomerTaskWithServicesDto](request.body) { dto =>
          val appointmentTask = PaidCustomerTaskWithAccommodations(dto.description, dto.address, dto.latitude, dto.longitude, dto.dateTime,
            CustomerPaymentInformation(dto.paymentDetails.token, dto.paymentDetails.cardId), dto.paymentDetails.promotion, dto.service, dto.extras)
          createTask(appointmentTask, dto.vehicleId)
        }
      case "v2" =>
        processRequestF[CustomerTaskDto](request.body) { dto =>
          val appointmentTask = PaidCustomerTaskWithInteriorCleaning(dto.description, dto.address, dto.latitude, dto.longitude, dto.dateTime,
            CustomerPaymentInformation(dto.paymentDetails.token, dto.paymentDetails.cardId), dto.paymentDetails.promotion, dto.paymentDetails.hasInteriorCleaning)
          createTask(appointmentTask, dto.vehicleId)
        }
      case _ =>
        processRequestF[TaskDto](request.body) { dto =>
          val appointmentTask = PaidCustomerTaskWithInteriorCleaning(dto.description, dto.pickupAddress, dto.pickupLatitude, dto.pickupLongitude, dto.pickupDateTime,
            CustomerPaymentInformation(dto.token, dto.cardId), dto.promotion, dto.hasInteriorCleaning)
          createTask(appointmentTask, dto.vehicleId)
        }
    }
  }

  def createAnonymousTask(version: String) = Action.async(BodyParsers.parse.json) { request =>
    def createTask(appointmentTask: PaidAppointmentTask, user: User, vehicle: Vehicle) = {
      taskService.createTaskForAnonymous(appointmentTask, user, vehicle) map {
        case Left(error) => badRequest(error.message)
        case Right(tookanTask) => ok(tookanTask)
      }
    }

    version match {
      case "v3" =>
        processRequestF[AnonymousTaskWithServicesDto](request.body) { dto =>
          val appointmentTask = PaidAnonymousTaskWithAccommodations(dto.description, dto.address, dto.latitude, dto.longitude, dto.dateTime,
            AnonymousPaymentInformation(dto.paymentDetails.token), dto.paymentDetails.promotion, dto.service, dto.extras)
          createTask(appointmentTask, dto.userDto.mapToUser, dto.vehicleDto.mapToVehicle)
        }
      case _ =>
        processRequestF[AnonymousTaskDto](request.body) { dto =>
          val appointmentTask = PaidAnonymousTaskWithInteriorCleaning(dto.description, dto.address, dto.latitude, dto.longitude, dto.dateTime,
            AnonymousPaymentInformation(dto.paymentDetails.token), dto.paymentDetails.promotion, dto.paymentDetails.hasInteriorCleaning)
          createTask(appointmentTask, dto.user, dto.vehicle.mapToVehicle)
        }
    }
  }

  def createPartnershipTask(version: String) = Action.async(BodyParsers.parse.json) { request =>
    def createPartnershipTask(appointmentTask: AppointmentTask, user: User, vehicle: Vehicle) = {
      taskService.createPartnershipTask(appointmentTask, user, vehicle) map {
        case Left(error) => badRequest(error.message)
        case Right(tookanTask) => ok(tookanTask)
      }
    }

    version match {
      case "v3" =>
        processRequestF[PartnershipTaskWithServiceDto](request.body) { dto =>
          createPartnershipTask(dto.mapToAppointmentTask, dto.userDto.mapToUser, dto.vehicleDto.mapToVehicle)
        }
      case _ =>
        processRequestF[PartnershipTaskDto](request.body) { dto =>
          createPartnershipTask(dto.mapToAppointmentTask, dto.user, dto.vehicleDto.mapToVehicle)
        }
    }
  }

  def cancelTask(version: String, id: Long) = authorized.async { request =>
    val userId = request.token.get.userInfo.id
    val selectQuery = for {
      (job, paymentDetails) <- Jobs join PaymentDetails on (_.id === _.jobId)
      if job.userId === userId && job.jobStatus.inSet(TaskStatuses.cancelableStatuses) && job.jobId === id
    } yield (job, paymentDetails)

    db.run(selectQuery.result.headOption).flatMap {
      _.map { row =>
        val job = row._1
        val paymentDetails = row._2

        val refundResult = paymentDetails.chargeId.map { chargeId =>
          Logger.debug(s"Refunding charge $chargeId for customer ${request.token.get.userInfo.email}")
          stripeService.refund(chargeId).map {
            case Left(_) =>
              Logger.debug(s"Can't refund money for job $id")
              Option(chargeId)
            case Right(_) =>
              Logger.debug(s"Refunded money for task with $id id")
              Option.empty
          }
        }.getOrElse(Future.successful(Option.empty))

        refundResult.flatMap { chargeOpt =>
          tookanService.updateTaskStatus(id, TaskStatuses.Cancel)
          val updateAction = DBIO.seq(
            Jobs.filter(_.id === job.id).map(_.jobStatus).update(TaskStatuses.Cancel.code),
            PaymentDetails.filter(_.jobId === job.id).map(_.chargeId).update(chargeOpt)
          ).transactionally
          db.run(updateAction).map(_ => success)
        }
      }.getOrElse(Future.successful(badRequest(s"Can't cancel task with $id id")))
    }
  }

  def getPendingTask = authorized.async { request =>
    taskService.pendingTasks(request.token.get.userInfo.id).map { resultSet =>
      val pendingTask = resultSet.headOption.map(row => convertToListDto(row))
      ok(pendingTask)
    }
  }

  def getPendingTasks = authorized.async { request =>
    taskService.pendingTasks(request.token.get.userInfo.id).map { resultSet =>
      val pendingTasks = resultSet.map(row => convertToListDto(row))
      ok(pendingTasks)
    }
  }

  def completeTask(version: String) = authorized.async(BodyParsers.parse.json) { request =>
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
                    val paymentSource = StripeService.PaymentSource(stripeId, tip.cardId)
                    stripeService.charge(tip.amount, paymentSource, dto.jobId)
                  }.getOrElse {
                    Future(Left(ErrorResponse("User doesn't set a payment method", VError)))
                  }
                }
              }

            paymentResult.flatMap {
              case Right(_) =>
                //TODO: check update statement
                val updateAction = DBIO.seq(
                  PaymentDetails.filter(_.jobId in taskQuery.map(_.id)).map(_.tip).update(tip.amount),
                  taskQuery.map(_.submitted).update(true)
                ).transactionally
                db.run(updateAction).map(_ => success)

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

  def listTasks(version: String, offset: Int, limit: Int) = authorized.async { request =>
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
      .map { resultSet =>
        val jobs = resultSet._2.map(row => convertToListDto(row)).toList
        ok(ListResponse(jobs, limit, offset, resultSet._1))
      }
  }

  def getTask(version: String, id: Long) = authorized.async { request =>
    val selectQuery = for {
      (((job, agent), vehicle), paymentDetails) <- Jobs joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id) join PaymentDetails on (_._1._1.id === _.jobId)
      if job.userId === request.token.get.userInfo.id && job.jobId === id
    } yield (job, agent, vehicle, paymentDetails)
    db.run(selectQuery.result.headOption).map(_.map { row =>
      ok(convertToDetailsDto(row))
    }.getOrElse(notFound))
  }

  def onTaskUpdate(version: String) = Action { implicit request =>
    val formData = Form(mapping(
      "job_id" -> Forms.longNumber,
      "job_status" -> Forms.number
    )(TaskHook.apply)(TaskHook.unapply)).bindFromRequest().get
    Logger.debug(s"Task update web hook. ${formData.toString}")
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
      (((job, agent), vehicle), paymentDetails) <- Jobs joinLeft Agents on (_.agentId === _.id) join Vehicles on (_._1.vehicleId === _.id) join PaymentDetails on (_._1._1.id === _.jobId)
      if job.userId === request.token.get.userInfo.id && job.jobStatus.inSet(TaskStatuses.activeStatuses)
    } yield (job, agent, vehicle, paymentDetails)
    db.run(selectQuery.sortBy(_._1.scheduledTime.asc).result.headOption).map { rowOpt =>
      val activeTaskOpt = rowOpt.map(row => convertToDetailsDto(row))
      ok(activeTaskOpt)
    }
  }

  private def convertToListDto[D](row: (JobsRow, Option[AgentsRow], VehiclesRow)) = {
    convertInternal(row._2, row._3) { (agentDto, vehicleDto) =>
      val job = row._1
      TaskListDto(job.jobId, job.scheduledTime.toLocalDateTime, agentDto, getJobImages(job), vehicleDto,
        job.jobStatus, job.submitted)
    }
  }

  private def convertToDetailsDto(row: (JobsRow, Option[AgentsRow], VehiclesRow, PaymentDetailsRow)) = {
    convertInternal(row._2, row._3) { (agentDto, vehicleDto) =>
      val job = row._1
      val paymentDetails = row._4
      TaskDetailsDto(job.jobId, job.scheduledTime.toLocalDateTime, agentDto, getJobImages(job), vehicleDto, job.jobStatus,
        job.submitted, job.teamName, job.jobAddress, job.jobPickupPhone, job.customerPhone, paymentDetails.paymentMethod,
        job.hasInteriorCleaning, paymentDetails.price, job.latitude, job.longitude, paymentDetails.promotion, paymentDetails.tip)
    }
  }

  private def convertInternal[D](agentOpt: Option[AgentsRow], vehicleRow: VehiclesRow)(consumer: (Option[AgentDto], VehicleDto) => D) = {
    val agent = agentOpt.map(agent => AgentDto(agent.fleetId, agent.name, agent.fleetImage, agent.phone))
      .orElse(None)
    val vehicle = VehicleDto(Some(vehicleRow.id), vehicleRow.makerId, vehicleRow.makerNiceName, vehicleRow.modelId,
      vehicleRow.modelNiceName, vehicleRow.yearId, vehicleRow.year, Option(vehicleRow.color), vehicleRow.licPlate)
    consumer.apply(agent, vehicle)
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

  implicit val vehicleDetailsFormat: Format[VehicleDetailsDto] = Json.format[VehicleDetailsDto]

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

  case class VehicleDetailsDto(maker: String,
                               model: String,
                               year: Int,
                               color: String,
                               licPlate: Option[String]) {
    def mapToVehicle: Vehicle = Vehicle.tupled(VehicleDetailsDto.unapply(this).get)
  }

  implicit val userDtoFormat: Format[UserDto] = Json.format[UserDto]

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
                                         dateTime: LocalDateTime, vehicleId: Int, service: Int, extras: Set[Int],
                                         paymentDetails: CustomerPaymentDetails)

  implicit val customerTaskWithServicesReads: Reads[CustomerTaskWithServicesDto] = (
    (__ \ "description").read[String] and
      (__ \ "address").read[String] and
      (__ \ "latitude").read[Double] and
      (__ \ "longitude").read[Double] and
      (__ \ "dateTime").read[LocalDateTime](dateTimeReads) and
      (__ \ "vehicleId").read[Int] and
      (__ \ "service").read[Int] and
      (__ \ "extras").read[Set[Int]] and
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

  case class AnonymousPaymentDetails(token: String, promotion: Option[Int])

  implicit val anonymousPaymentDetailsFormat: Format[AnonymousPaymentDetails] = Json.format[AnonymousPaymentDetails]

  case class AnonymousTaskWithServicesDto(description: String, userDto: UserDto, address: String, latitude: Double,
                                          longitude: Double, dateTime: LocalDateTime, vehicleDto: VehicleDetailsDto,
                                          service: Int, extras: Set[Int], paymentDetails: AnonymousPaymentDetails)

  implicit val anonymousTaskWithServicesReads: Reads[AnonymousTaskWithServicesDto] = (
    (__ \ "description").read[String] and
      (__ \ "user").read[UserDto] and
      (__ \ "address").read[String] and
      (__ \ "latitude").read[Double] and
      (__ \ "longitude").read[Double] and
      (__ \ "dateTime").read[LocalDateTime](dateTimeReads) and
      (__ \ "vehicle").read[VehicleDetailsDto] and
      (__ \ "service").read[Int] and
      (__ \ "extras").read[Set[Int]] and
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

  //  implicit val customerTaskWithServicesReads: Reads[CustomerTaskWithServicesDto] = (
  //    (__ \ "description").read[String] and
  //      (__ \ "address").read[String] and
  //      (__ \ "latitude").read[Double] and
  //      (__ \ "longitude").read[Double] and
  //      (__ \ "dateTime").read[LocalDateTime](dateTimeReads) and
  //      (__ \ "vehicleId").read[Int] and
  //      (__ \ "paymentDetails").read[CustomerPaymentDetails] and
  //      (__ \ "accommodation").read[Int] and
  //      (__ \ "extras").read[Set[Int]]
  //    ) (CustomerTaskWithServicesDto.apply _)

  //  implicit val anonymousTaskWithServicesReads: Reads[AnonymousTaskWithServicesDto] = (
  //    (__ \ "description").read[String] and
  //      (__ \ "name").read[String] and
  //      (__ \ "email").readNullable[String] and
  //      (__ \ "phone").read[String] and
  //      (__ \ "address").read[String] and
  //      (__ \ "latitude").read[Double] and
  //      (__ \ "longitude").read[Double] and
  //      (__ \ "dateTime").read[LocalDateTime](dateTimeReads) and
  //      (__ \ "vehicle").read[VehicleDetailsDto] and
  //      (__ \ "paymentDetails").read[AnonymousPaymentDetails] and
  //      (__ \ "accommodation").read[Int] and
  //      (__ \ "extras").read[Set[Int]]
  //    ) (AnonymousTaskWithServicesDto.apply _)

  case class TipDto(amount: Int,
                    cardId: Option[String],
                    token: Option[String])

  case class CompleteTaskDto(jobId: Long,
                             tip: Option[TipDto])

  case class TaskHook(jobId: Long,
                      jobStatus: Int)

}


