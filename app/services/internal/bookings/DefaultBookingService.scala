package services.internal.bookings

import java.sql.Date
import java.time.{LocalDate, LocalDateTime}
import javax.inject.Inject

import commons.monads.transformers.OptionT
import commons.utils.TimeUtils
import dao.dayslots.BookingDao.BookingSlot
import dao.dayslots._
import models.Tables
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

  override def getBookingSlots(startDate: LocalDate = LocalDate.now(),
                               endDate: LocalDate = LocalDate.now().plusDays(14),
                               filterByCurrentTime: Boolean = true): Future[Seq[BookingSlot]] = {
    val date = startDate.toSqlDate
    bookingDao.findBookingSlots(date, endDate.toSqlDate).map { bookingSlots =>
      if (filterByCurrentTime) {
        bookingSlots
          .map(bookingSlot => filterTimeSlots(bookingSlot, date))
      } else {
        bookingSlots
      }
    }
  }

  private def filterTimeSlots(bookingSlot: BookingSlot, startDate: Date) = {
    if (bookingSlot.daySlot.date.after(startDate)) {
      bookingSlot
    } else {
      val currentTime = LocalDateTime.now().toSqlTime
      val availableTimeSlots = bookingSlot.timeSlots
        .filter(timeSlot => timeSlot.startTime.after(currentTime))
      bookingSlot.copy(timeSlots = availableTimeSlots)
    }
  }

  override def findTimeSlot(id: Int): Future[Option[TimeSlotsRow]] = {
    bookingDao.findTimeSlotById(id)
  }

  override def increaseCapacity(id: Int, newCapacity: Int): Future[Option[Tables.TimeSlotsRow]] = {
    bookingDao.findTimeSlotById(id).flatMap {
      case Some(timeSlot) if timeSlot.capacity <= newCapacity =>
        val updatedTimeSlot = timeSlot.copy(capacity = newCapacity)
        Future.successful(Some(updatedTimeSlot))
      case _ =>
        Future.successful(None)
    }
  }
}
