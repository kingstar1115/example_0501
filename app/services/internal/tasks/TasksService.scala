package services.internal.tasks

import java.time.LocalDateTime

import com.google.inject.ImplementedBy
import commons.ServerError
import controllers.rest.TasksController.{CompleteTaskDto, TaskDetailsDto}
import models.Tables.{AgentsRow, TasksRow, VehiclesRow}
import services.TookanService.{AppointmentDetails, AppointmentResponse}
import services.internal.tasks.TasksService._

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultTaskService])
trait TasksService {

  def createTaskForCustomer(userId: Int, vehicle: Int)(implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, AppointmentResponse]]

  def createTaskForAnonymous(user: User, vehicle: Vehicle)(implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, AppointmentResponse]]

  def createPartnershipTask(user: User, vehicle: Vehicle)(implicit appointmentTask: AppointmentTask): Future[Either[ServerError, AppointmentResponse]]

  def refreshTask(taskId: Long): Future[Either[String, AppointmentDetails]]

  def pendingTasks(userId: Int): Future[Seq[(TasksRow, Option[AgentsRow], VehiclesRow)]]

  def completeTask(dto: CompleteTaskDto, userId: Int): Future[Either[ServerError, TasksRow]]

  def getTask(id: Long, userId: Int): Future[Option[TaskDetailsDto]]
}

object TasksService {


  trait AbstractUser {
    def name: String

    def phone: String

    def email: Option[String]
  }

  case class User(name: String, phone: String, email: Option[String]) extends AbstractUser

  case class PersistedUser(id: Int, name: String, phone: String, email: Option[String], stripeId: Option[String]) extends AbstractUser

  trait AbstractVehicle {
    def maker: String

    def model: String

    def year: Int

    def color: String

    def licPlate: Option[String]
  }

  case class Vehicle(maker: String, model: String, year: Int, color: String, licPlate: Option[String])
    extends AbstractVehicle

  case class PersistedVehicle(maker: String, model: String, year: Int, color: String, licPlate: Option[String], id: Int, userId: Int)
    extends AbstractVehicle


  abstract sealed class PaymentInformation

  case class CustomerPaymentInformation(token: Option[String], cardId: Option[String]) extends PaymentInformation

  case class AnonymousPaymentInformation(token: String) extends PaymentInformation

  trait AppointmentTask {
    def description: String

    def address: String

    def latitude: Double

    def longitude: Double

    def dateTime: LocalDateTime
  }

  trait PaidAppointmentTask extends AppointmentTask {
    def paymentInformation: PaymentInformation

    def promotion: Option[Int]

    def discount: Option[Int]
  }

  trait PaidAnonymousAppointmentTask extends PaidAppointmentTask {
    def tip: Option[Int]
  }


  trait ServicesInformation {
    def serviceId: Int

    def extras: Set[Int]
  }

  trait InteriorCleaning {
    def hasInteriorCleaning: Boolean
  }

  trait ZonedTimeSlot {
    def timeSlot: Int
  }

  case class PaidCustomerTaskWithInteriorCleaning(description: String,
                                                  address: String,
                                                  latitude: Double,
                                                  longitude: Double,
                                                  dateTime: LocalDateTime,
                                                  paymentInformation: CustomerPaymentInformation,
                                                  promotion: Option[Int],
                                                  hasInteriorCleaning: Boolean) extends PaidAppointmentTask with InteriorCleaning {
    override def discount: Option[Int] = None
  }

  case class PaidCustomerTaskWithAccommodations(description: String,
                                                address: String,
                                                latitude: Double,
                                                longitude: Double,
                                                dateTime: LocalDateTime,
                                                paymentInformation: CustomerPaymentInformation,
                                                promotion: Option[Int],
                                                serviceId: Int,
                                                extras: Set[Int]) extends PaidAppointmentTask with ServicesInformation {
    override def discount: Option[Int] = None
  }

  case class PaidCustomerTaskWithTimeSlot(description: String,
                                          address: String,
                                          latitude: Double,
                                          longitude: Double,
                                          timeSlot: Int,
                                          paymentInformation: CustomerPaymentInformation,
                                          promotion: Option[Int],
                                          discount: Option[Int],
                                          serviceId: Int,
                                          extras: Set[Int]) extends PaidAppointmentTask with ServicesInformation with ZonedTimeSlot {
    override def dateTime: LocalDateTime = throw new UnsupportedOperationException("Use time slot id instead of date/time")
  }

  case class PaidAnonymousTaskWithInteriorCleaning(description: String,
                                                   address: String,
                                                   latitude: Double,
                                                   longitude: Double,
                                                   dateTime: LocalDateTime,
                                                   paymentInformation: AnonymousPaymentInformation,
                                                   promotion: Option[Int],
                                                   hasInteriorCleaning: Boolean) extends PaidAppointmentTask with InteriorCleaning {
    override def discount: Option[Int] = None
  }

  case class PaidAnonymousTaskWithAccommodations(description: String,
                                                 address: String,
                                                 latitude: Double,
                                                 longitude: Double,
                                                 dateTime: LocalDateTime,
                                                 paymentInformation: AnonymousPaymentInformation,
                                                 promotion: Option[Int],
                                                 tip: Option[Int],
                                                 serviceId: Int,
                                                 extras: Set[Int]) extends PaidAnonymousAppointmentTask with ServicesInformation {
    override def discount: Option[Int] = None
  }

  case class PaidAnonymousTaskWithTimeSlot(description: String,
                                           address: String,
                                           latitude: Double,
                                           longitude: Double,
                                           timeSlot: Int,
                                           paymentInformation: AnonymousPaymentInformation,
                                           promotion: Option[Int],
                                           tip: Option[Int],
                                           serviceId: Int,
                                           extras: Set[Int],
                                           discount: Option[Int]) extends PaidAnonymousAppointmentTask with ServicesInformation with ZonedTimeSlot {
    override def dateTime: LocalDateTime = throw new UnsupportedOperationException("Use time slot id instead of date/time")
  }

  case class PartnershipTaskWithInteriorCleaning(description: String,
                                                 address: String,
                                                 latitude: Double,
                                                 longitude: Double,
                                                 dateTime: LocalDateTime,
                                                 hasInteriorCleaning: Boolean) extends AppointmentTask with InteriorCleaning

  case class PartnershipTaskWithAccommodations(description: String,
                                               address: String,
                                               latitude: Double,
                                               longitude: Double,
                                               dateTime: LocalDateTime,
                                               serviceId: Int,
                                               extras: Set[Int]) extends AppointmentTask with ServicesInformation

}
