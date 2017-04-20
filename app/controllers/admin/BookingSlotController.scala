package controllers.admin

import java.time.LocalDate
import javax.inject.Inject

import controllers.admin.BookingSlotController._
import dto.BookingDto._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, Controller}
import services.internal.bookings.BookingService
import views.html.admin.booking.booking
import views.html.admin.booking.timeslot.timeslot

import scala.concurrent.ExecutionContext.Implicits.global


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
        Ok(timeslot(getForm.fill(TimeSlotForm(timeSlotRow.id, timeSlotRow.startTime.toString, timeSlotRow.endTime.toString,
          timeSlotRow.capacity, timeSlotRow.bookingsCount))))
    }
  }

  def saveBookingSlot() = Action { implicit request =>
    getForm.bindFromRequest().fold(
      formWithErrors =>
        BadRequest(timeslot(formWithErrors)),
      form => {
        bookingService.findTimeSlot(form.id)
        Redirect(controllers.admin.routes.BookingSlotController.getBookingSlots())
      }
    )
  }
}

object BookingSlotController {

  case class TimeSlotForm(id: Int, startTime: String, endTime: String, capacity: Int, reserved: Int)

  def getForm: Form[TimeSlotForm] = Form(
    mapping(
      "id" -> number,
      "startTime" -> text,
      "endTime" -> text,
      "capacity" -> number(min = 1),
      "reserved" -> number
    )(TimeSlotForm.apply)(TimeSlotForm.unapply)
  )
}
