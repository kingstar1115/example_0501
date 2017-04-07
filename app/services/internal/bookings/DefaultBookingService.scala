package services.internal.bookings

import java.time.LocalDateTime
import javax.inject.Inject

import commons.monads.transformers.OptionT
import commons.utils.TimeUtils
import dao.dayslots._
import models.Tables._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class DefaultBookingService @Inject()(daySlotsDao: BookingDao) extends BookingService with TimeUtils {

  override def findFreeTimeSlotByTimestamp(timestamp: LocalDateTime): Future[Option[TimeSlotsRow]] = {
    val bookingDate = timestamp.toSqlDate
    val bookingTime = timestamp.toSqlTime
    daySlotsDao.findFreeTimeSlotByDateTime(bookingDate, bookingTime)
  }

  override def reserveBooking(dateTime: LocalDateTime): Future[Option[TimeSlotsRow]] = {
    if (LocalDateTime.now().isBefore(dateTime)) {
      (for {
        timeSlot <- OptionT(findFreeTimeSlotByTimestamp(dateTime))
        updatedTimeSlot <- OptionT(reserveBookingInternal(timeSlot))
      } yield updatedTimeSlot).inner
    } else {
      Future.successful(None)
    }
  }

  private def reserveBookingInternal(timeSlot: TimeSlotsRow): Future[Option[TimeSlotsRow]] = {
    daySlotsDao.increaseBooking(timeSlot).map {
      case 1 =>
        Some(timeSlot.copy(bookingsCount = timeSlot.bookingsCount + 1))
      case _ =>
        None
    }
  }

  def releaseBooking(timeSlot: TimeSlotsRow): Future[TimeSlotsRow] = {
    daySlotsDao.decreaseBooking(timeSlot)
  }
}
