package controllers.admin

import java.time.{DayOfWeek, LocalDate}
import javax.inject.Inject

import commons.ServerError
import commons.enums.ValidationError
import controllers.admin.BookingSlotController._
import dao.dayslots.BookingDao
import dto.BookingDto._
import forms.bookings.TimeSlotForm
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.{Action, Controller}
import services.internal.bookings.BookingService
import views.html.admin.booking.{booking, bookingTabs}
import views.html.admin.booking.timeslot.timeslot
import views.html.admin.notFound

import scala.concurrent.Future


//noinspection TypeAnnotation
class BookingSlotController @Inject()(bookingService: BookingService,
                                      val messagesApi: MessagesApi) extends Controller with I18nSupport {

  def getBookingSlots = Action.async { implicit request =>
    getBookingSlotsInternal(LocalDate.now()) { (daySlotDtos, prevDate, nextDate) =>
      Ok(booking(daySlotDtos, prevDate, nextDate))
    }
  }

  def getPartialBookingSlots(date: LocalDate) = Action.async { _ =>
    getBookingSlotsInternal(date) { (daySlotDtos, prevDate, nextDate) =>
      Ok(bookingTabs(daySlotDtos, prevDate, nextDate))
    }
  }

  private def getBookingSlotsInternal[T](date: LocalDate)(mapper: (Seq[DaySlotDto], Option[LocalDate], Option[LocalDate]) => T): Future[T] = {
    val bookingRange = getBookingRange(date)
    (for {
      bookingSlots <- bookingService.getBookingSlots(bookingRange.start, bookingRange.end)
      hasNext <- bookingService.hasBookingSlotsAfterDate(bookingRange.end.plusDays(1))
    } yield (bookingSlots, hasNext)).map {
      case (bookingSlots: Seq[BookingDao.BookingSlot], hasNext: Boolean) =>
        val daySlotDtos = bookingSlots.map(DaySlotDto.fromBookingSlot)
        mapper(daySlotDtos, getPrevDate(bookingRange), getNextDate(bookingRange, hasNext))
    }
  }

  private def getBookingRange(date: LocalDate): BookingInterval = {
    if (LocalDate.now().equals(date)) {
      (date, date.`with`(DayOfWeek.SUNDAY))
    } else {
      (date.`with`(DayOfWeek.MONDAY), date.`with`(DayOfWeek.SUNDAY))
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

  def getBookingSlot(id: Int) = Action.async { _ =>
    bookingService.findTimeSlot(id).map {
      case None =>
        BadRequest
      case Some(timeSlotRow) =>
        Ok(timeslot(TimeSlotForm(timeSlotRow.id, timeSlotRow.startTime.toString, timeSlotRow.endTime.toString,
          timeSlotRow.capacity, timeSlotRow.reserved).wrapToForm()))
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

  type BookingInterval = (LocalDate, LocalDate)

  implicit class RichBookingRange(bookingRange: BookingInterval) {
    def start: LocalDate = bookingRange._1

    def end: LocalDate = bookingRange._2
  }

}
