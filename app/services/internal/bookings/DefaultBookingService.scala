package services.internal.bookings

import java.time.{LocalDate, LocalDateTime}
import javax.inject.Inject

import commons.monads.transformers.OptionT
import commons.utils.TimeUtils
import dao.dayslots.BookingDao.BookingSlot
import dao.dayslots._
import models.Tables._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class DefaultBookingService @Inject()(bookingDao: BookingDao) extends BookingService with TimeUtils {

  override def findFreeTimeSlot(timestamp: LocalDateTime): Future[Option[TimeSlotsRow]] = {
    val bookingDate = timestamp.toSqlDate
    val bookingTime = timestamp.toSqlTime
    bookingDao.findFreeTimeSlotByDateTime(bookingDate, bookingTime)
  }

  override def reserveBooking(dateTime: LocalDateTime): Future[Option[TimeSlotsRow]] = {
    if (LocalDateTime.now().isBefore(dateTime)) {
      (for {
        timeSlot <- OptionT(findFreeTimeSlot(dateTime))
        updatedTimeSlot <- OptionT(reserveBookingInternal(timeSlot))
      } yield updatedTimeSlot).inner
    } else {
      Future.successful(None)
    }
  }

  private def reserveBookingInternal(timeSlot: TimeSlotsRow): Future[Option[TimeSlotsRow]] = {
    bookingDao.increaseBooking(timeSlot).map {
      case 1 =>
        Some(timeSlot.copy(bookingsCount = timeSlot.bookingsCount + 1))
      case _ =>
        None
    }
  }

  def releaseBooking(timeSlot: TimeSlotsRow): Future[TimeSlotsRow] = {
    bookingDao.decreaseBooking(timeSlot)
  }

  override def getBookingSlots: Future[Seq[BookingSlot]] = {
    val currentDate = LocalDate.now().atStartOfDay().toSqlDate
    bookingDao.findBookingSlots(currentDate).map { bookingSlots =>
      val currentTime = LocalDateTime.now().toSqlTime
      bookingSlots.map { bookingSlot =>
        if (bookingSlot.daySlot.date.after(currentDate)) {
          bookingSlot
        } else {
          val availableTimeSlots = bookingSlot.timeSlots
            .filter(timeSlot => timeSlot.startTime.after(currentTime))
          bookingSlot.copy(timeSlots = availableTimeSlots)
        }
      }
    }
  }
}
