package controllers.admin

import java.time.{DayOfWeek, LocalDate}

import commons.ServerError
import commons.enums.ValidationError
import controllers.admin.BookingSlotController._
import dto.BookingDto._
import forms.bookings.TimeSlotForm
import javax.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Action, Controller}
import services.internal.bookings.BookingService
import views.html.admin.booking.booking
import views.html.admin.booking.timeslot.timeslot
import views.html.admin.notFound

import scala.concurrent.Future


//noinspection TypeAnnotation
class BookingSlotController @Inject()(bookingService: BookingService,
                                      val messagesApi: MessagesApi) extends Controller with I18nSupport {

  def getBookingSlots(countryId: Int, date: LocalDate = LocalDate.now()) = Action.async { implicit request =>
    val bookingRange = getBookingRange(date)
    (for {
      countryDaySlots <- bookingService.getBookingSlotsByCountries(Set(countryId) ,bookingRange.start, bookingRange.end)
      hasNext <- bookingService.hasBookingSlotsAfterDate(bookingRange.end.plusDays(1))
    } yield (countryDaySlots, hasNext)).map {
      case (Seq(countryWithDaySlots), hasNext: Boolean) =>
        val countryDto =  CountryDto(countryWithDaySlots)
        val prevDate = getPrevDate(bookingRange)
        val nextDate = getNextDate(bookingRange, hasNext)
        val selectedDate = if (countryDto.daySlots.isEmpty || countryDto.daySlots.exists(daySlot => daySlot.date.isEqual(date))) {
          date
        } else {
          countryDto.daySlots.head.date
        }
        Ok(booking(countryDto, prevDate, nextDate, selectedDate))
      case _ =>
        BadRequest(notFound())
    }
  }

  private def getBookingRange(date: LocalDate): BookingInterval = {
    val monday = date.`with`(DayOfWeek.MONDAY)
    val sunday = date.`with`(DayOfWeek.SUNDAY)
    val now = LocalDate.now()
    if (now.compareTo(monday) >= 0) {
      BookingInterval(now, sunday)
    } else {
      BookingInterval(monday, sunday)
    }
  }

  private def getPrevDate(bookingInterval: BookingInterval): Option[LocalDate] = {
    val now = LocalDate.now()
    if (now.equals(bookingInterval.start)) {
      None
    } else if (bookingInterval.start.minusDays(7).isBefore(now)) {
      Some(now)
    } else {
      Some(bookingInterval.start.minusDays(7))
    }
  }

  private def getNextDate(bookingInterval: BookingInterval, hasNext: Boolean): Option[LocalDate] = {
    if (hasNext) {
      Some(bookingInterval.end.plusDays(1))
    } else {
      None
    }
  }

  def getBookingSlot(id: Int, countryId: Int, date: LocalDate = LocalDate.now()) = Action.async { _ =>
    bookingService.findTimeSlot(id).map {
      case None =>
        BadRequest
      case Some(timeSlotRow) =>
        Ok(timeslot(TimeSlotForm(timeSlotRow.id, countryId ,date.toString, timeSlotRow.startTime.toString,
          timeSlotRow.endTime.toString, timeSlotRow.capacity, timeSlotRow.reserved).wrapToForm()))
    }
  }

  def updateBookingSlot(countryId: Int, id: Int) = Action.async { implicit request =>
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
            Redirect(controllers.admin.routes.BookingSlotController.getBookingSlots(form.countryId, LocalDate.parse(form.date)))
        }
      }
    )
  }
}

object BookingSlotController {

  case class BookingInterval(start: LocalDate, end: LocalDate)

}
