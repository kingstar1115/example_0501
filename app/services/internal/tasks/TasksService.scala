package services.internal.tasks

import java.time.LocalDateTime

import com.google.inject.ImplementedBy
import commons.ServerError
import models.Tables.{AgentsRow, JobsRow, VehiclesRow}
import services.TookanService.AppointmentResponse
import services.internal.tasks.TasksService._

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultTaskService])
trait TasksService {

  def createTaskForCustomer(userId: Int)(implicit taskDto: CustomerTaskDto): Future[Either[ServerError, AppointmentResponse]]

  def createTaskForAnonymous(implicit taskDto: AnonymousTaskDto): Future[Either[ServerError, AppointmentResponse]]

  def createPartnershipTask(implicit taskDto: PartnershipTaskDto): Future[Either[ServerError, AppointmentResponse]]

  @Deprecated
  def createTask(dto: TaskDto, userId: Int): Future[Either[ServerError, AppointmentResponse]]

  def refreshTask(taskId: Long): Unit

  def pendingTasks(userId: Int): Future[Seq[(JobsRow, Option[AgentsRow], VehiclesRow)]]
}

object TasksService {

  trait PaymentDetails {
    def promotion: Option[Int]

    def hasInteriorCleaning: Boolean
  }

  case class CustomerPaymentDetails(promotion: Option[Int],
                                    hasInteriorCleaning: Boolean,
                                    token: Option[String],
                                    cardId: Option[String]) extends PaymentDetails

  case class AnonymousPaymentDetails(token: String,
                                     promotion: Option[Int],
                                     hasInteriorCleaning: Boolean) extends PaymentDetails

  case class PartnershipPaymentDetails(promotion: Option[Int],
                                       hasInteriorCleaning: Boolean) extends PaymentDetails

  case class VehicleDetailsDto(maker: String,
                               model: String,
                               year: Int,
                               color: String,
                               licPlate: Option[String])


  trait BaseTaskDto {
    def description: String

    def name: String

    def email: Option[String]

    def phone: String

    def address: String

    def latitude: Double

    def longitude: Double

    def dateTime: LocalDateTime

    def paymentDetails: PaymentDetails
  }

  case class CustomerTaskDto(description: String,
                             name: String,
                             email: Option[String],
                             phone: String,
                             address: String,
                             latitude: Double,
                             longitude: Double,
                             dateTime: LocalDateTime,
                             vehicleId: Int,
                             paymentDetails: CustomerPaymentDetails) extends BaseTaskDto


  case class AnonymousTaskDto(description: String,
                              name: String,
                              email: Option[String],
                              phone: String,
                              address: String,
                              latitude: Double,
                              longitude: Double,
                              dateTime: LocalDateTime,
                              vehicle: VehicleDetailsDto,
                              paymentDetails: AnonymousPaymentDetails) extends BaseTaskDto

  case class PartnershipTaskDto(description: String,
                                name: String,
                                email: Option[String],
                                phone: String,
                                address: String,
                                latitude: Double,
                                longitude: Double,
                                dateTime: LocalDateTime,
                                vehicle: VehicleDetailsDto,
                                paymentDetails: PartnershipPaymentDetails) extends BaseTaskDto

  case class TaskDto(token: Option[String],
                     cardId: Option[String],
                     description: String,
                     pickupName: String,
                     pickupEmail: Option[String],
                     pickupPhone: String,
                     pickupAddress: String,
                     pickupLatitude: Double,
                     pickupLongitude: Double,
                     pickupDateTime: LocalDateTime,
                     hasInteriorCleaning: Boolean,
                     vehicleId: Int,
                     promotion: Option[Int])

}
