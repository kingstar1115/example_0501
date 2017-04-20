package controllers.admin

import java.time.LocalDate
import javax.inject.Inject

import commons.ServerError
import commons.enums.ValidationError
import controllers.admin.BookingSlotController._
import dto.BookingDto._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, Controller}
import services.internal.bookings.BookingService
import views.html.admin.booking.booking
import views.html.admin.booking.timeslot.timeslot
import views.html.admin.notFound

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class BookingSlotController @Inject()(bookingService: BookingService,
                                      val messagesApi: MessagesApi) extends Controller with I18nSupport {

  def getBookingSlots = Action.async { _ =>
    bookingService.getBookingSlots(endDate = LocalDate.now().plusDays(6), filterByCurrentTime = false)
      .map(DaySlotDto.fromBookingSlots)
      .map(bookingDaySlots => Ok(booking(bookingDaySlots)))
  }

  def getBookingSlot(id: Int) = Action.async { _ =>
    bookingService.findTimeSlot(id).map {
      case None =>
        BadRequest
      case Some(timeSlotRow) =>
        Ok(timeslot(TimeSlotForm(timeSlotRow.id, timeSlotRow.startTime.toString, timeSlotRow.endTime.toString,
          timeSlotRow.capacity, timeSlotRow.bookingsCount).wrapToForm()))
    }
  }

  def updateBookingSlot(id: Int) = Action.async { implicit request =>
    TimeSlotForm.form().bindFromRequest().fold(
      formWithErrors =>
        Future.successful(BadRequest(timeslot(formWithErrors))),
      form => {
        bookingService.increaseCapacity(id, form.capacity).map {
          case Left(ServerError(message, Some(ValidationError))) =>
            BadRequest(timeslot(form.wrapToForm().withError(TimeSlotForm.Capacity, message)))
          case Left(_) =>
            BadRequest(notFound())
          case Right(_) =>
            Redirect(controllers.admin.routes.BookingSlotController.getBookingSlots())
        }
      }
    )
  }
}

object BookingSlotController {

  case class TimeSlotForm(id: Int, startTime: String, endTime: String, capacity: Int, reserved: Int) {
    def wrapToForm(): Form[TimeSlotForm] = TimeSlotForm.form().fill(this)
  }

  object TimeSlotForm {
    val Id = "id"
    val StartTime = "startTime"
    val EndTime = "endTime"
    val Capacity = "capacity"
    val Reserved = "reserved"

    def form() = Form(
      mapping(
        Id -> number,
        StartTime -> text,
        EndTime -> text,
        Capacity -> number(min = 1),
        Reserved -> number
      )(TimeSlotForm.apply)(TimeSlotForm.unapply)
    )
  }

}
