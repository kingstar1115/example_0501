package services.internal.tasks

import java.time.LocalDateTime

import com.google.inject.ImplementedBy
import commons.ServerError
import models.Tables.{AgentsRow, TasksRow, VehiclesRow}
import services.TookanService.AppointmentResponse
import services.internal.tasks.TasksService._

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultTaskService])
trait TasksService {

  def createTaskForCustomer(userId: Int, vehicle: Int)(implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, AppointmentResponse]]

  def createTaskForAnonymous(user: User, vehicle: Vehicle)(implicit appointmentTask: PaidAppointmentTask): Future[Either[ServerError, AppointmentResponse]]

  def createPartnershipTask(user: User, vehicle: Vehicle)(implicit appointmentTask: AppointmentTask): Future[Either[ServerError, AppointmentResponse]]

  def refreshTask(taskId: Long): Unit

  def pendingTasks(userId: Int): Future[Seq[(TasksRow, Option[AgentsRow], VehiclesRow)]]
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


  trait ServicesInformation {
    def serviceId: Int

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
                                                serviceId: Int,
                                                extras: Set[Int]) extends PaidAppointmentTask with ServicesInformation

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
                                                 serviceId: Int,
                                                 extras: Set[Int]) extends PaidAppointmentTask with ServicesInformation

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
