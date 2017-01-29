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

  def createTaskForCustomer(implicit appointmentTask: PaidAppointmentTask, userId: Int, vehicle: Int): Future[Either[ServerError, AppointmentResponse]]

  def createTaskForAnonymous(implicit appointmentTask: PaidAppointmentTask, user: User, vehicle: Vehicle): Future[Either[ServerError, AppointmentResponse]]

  def createPartnershipTask(implicit appointmentTask: AppointmentTask, user: User, vehicle: Vehicle): Future[Either[ServerError, AppointmentResponse]]

  def refreshTask(taskId: Long): Unit

  def pendingTasks(userId: Int): Future[Seq[(JobsRow, Option[AgentsRow], VehiclesRow)]]
}

object TasksService {


  trait AbstractUser {
    def name: String

    def phone: String

    def email: Option[String]
  }

  case class User(name: String, phone: String, email: Option[String]) extends AbstractUser

  case class PersistedUser(id: Int, name: String, phone: String, email: Option[String], stipeId: Option[String]) extends AbstractUser

  trait AbstractVehicle {
    def maker: String

    def model: String

    def year: Int

    def color: String

    def licPlate: Option[String]
  }

  case class Vehicle(maker: String, model: String, year: Int, color: String, licPlate: Option[String])
    extends AbstractVehicle

  case class PersistedVehicle(maker: String, model: String, year: Int, color: String, licPlate: Option[String], id: Int)
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
  }


  trait Accommodation {
    def accommodation: Int

    def extras: Set[Int]
  }

  trait InteriorCleaning {
    def hasInteriorCleaning: Boolean
  }

  case class PaidCustomerTaskWithInteriorCleaning(description: String,
                                                  address: String,
                                                  latitude: Double,
                                                  longitude: Double,
                                                  dateTime: LocalDateTime,
                                                  paymentInformation: CustomerPaymentInformation,
                                                  promotion: Option[Int],
                                                  hasInteriorCleaning: Boolean) extends PaidAppointmentTask with InteriorCleaning

  case class PaidCustomerTaskWithAccommodations(description: String,
                                                address: String,
                                                latitude: Double,
                                                longitude: Double,
                                                dateTime: LocalDateTime,
                                                paymentInformation: CustomerPaymentInformation,
                                                promotion: Option[Int],
                                                accommodation: Int,
                                                extras: Set[Int]) extends PaidAppointmentTask with Accommodation

  case class PaidAnonymousTaskWithInteriorCleaning(description: String,
                                                   address: String,
                                                   latitude: Double,
                                                   longitude: Double,
                                                   dateTime: LocalDateTime,
                                                   paymentInformation: AnonymousPaymentInformation,
                                                   promotion: Option[Int],
                                                   hasInteriorCleaning: Boolean) extends PaidAppointmentTask with InteriorCleaning

  case class PaidAnonymousTaskWithAccommodations(description: String,
                                                 address: String,
                                                 latitude: Double,
                                                 longitude: Double,
                                                 dateTime: LocalDateTime,
                                                 paymentInformation: AnonymousPaymentInformation,
                                                 promotion: Option[Int],
                                                 accommodation: Int,
                                                 extras: Set[Int]) extends PaidAppointmentTask with Accommodation

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
                                               accommodation: Int,
                                               extras: Set[Int]) extends AppointmentTask with Accommodation

}
