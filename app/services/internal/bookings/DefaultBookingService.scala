package services.internal.bookings

import java.sql.Date
import java.time.{LocalDate, LocalDateTime}
import javax.inject.Inject

import commons.ServerError
import commons.SettingConstants.Booking._
import commons.enums.ValidationError
import commons.monads.transformers.OptionT
import commons.utils.TimeUtils._
import dao.SlickDbService
import dao.dayslots.BookingDao.BookingSlot
import dao.dayslots._
import models.Tables._
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import services.internal.settings.SettingsService

import scala.concurrent.Future


class DefaultBookingService @Inject()(bookingDao: BookingDao,
                                      slickDbService: SlickDbService,
                                      settingsService: SettingsService) extends BookingService {

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
        Some(timeSlot.copy(reserved = timeSlot.reserved + 1))
      case _ =>
        None
    }
  }

  def releaseBooking(timeSlot: TimeSlotsRow): Future[TimeSlotsRow] = {
    bookingDao.decreaseBooking(timeSlot)
  }

  override def getBookingSlots(startDate: LocalDate = LocalDate.now(),
                               endDate: LocalDate = LocalDate.now().plusDays(14)): Future[Seq[BookingSlot]] = {
    bookingDao.findBookingSlots(startDate.toSqlDate, endDate.toSqlDate).map { bookingSlots =>
      bookingSlots
        .map(bookingSlot => filterTimeSlots(bookingSlot))
        .filterNot(_.timeSlots.isEmpty)
    }
  }

  private def filterTimeSlots(bookingSlot: BookingSlot) = {
    if (bookingSlot.daySlot.date.after(LocalDate.now().toSqlDate)) {
      bookingSlot
    } else {
      val currentTime = LocalDateTime.now().toSqlTime
      val availableTimeSlots = bookingSlot.timeSlots
        .filter(timeSlot => timeSlot.startTime.after(currentTime))
      bookingSlot.copy(timeSlots = availableTimeSlots)
    }
  }

  override def findTimeSlot(id: Int): Future[Option[TimeSlotsRow]] = {
    slickDbService.findOneOption(TimeSlotQueryObject.findByIdQuery(id))
  }

  override def increaseCapacity(id: Int, newCapacity: Int): Future[Either[ServerError, TimeSlotsRow]] = {
    findTimeSlot(id).flatMap {
      case Some(timeSlot) if timeSlot.reserved <= newCapacity =>
        val updatedTimeSlot = timeSlot.copy(capacity = newCapacity)
        slickDbService.run(TimeSlotQueryObject.updateQuery(updatedTimeSlot))
          .map(_ => Right(updatedTimeSlot))
      case Some(timeSlot) =>
        Future.successful(Left(ServerError(s"Capacity must be greater than reserved amount ${timeSlot.reserved}", Some(ValidationError))))
      case _ =>
        Future.successful(Left(ServerError(s"Time Slot with id: '$id' not found")))
    }
  }

  override def hasBookingSlotsAfterDate(date: LocalDate): Future[Boolean] = {
    bookingDao.hasBookingSlotsAfterDate(date.toSqlDate)
  }

  override def createDaySlotWithTimeSlots(date: Date): Future[(DaySlotsRow, Seq[TimeSlotsRow])] = {
    Logger.info(s"Creating booking slot for '$date'")
    (for {
      dayCapacity <- settingsService.getIntValue(DaySlotCapacity, DefaultDaySlotCapacity)
      timeCapacity <- settingsService.getIntValue(TimeSlotCapacity, DefaultTimeSlotCapacity)
    } yield (dayCapacity, timeCapacity)).flatMap {
      case (dayCapacity, timeCapacity) =>
        val daySlot = DaySlotsRow(0, currentTimestamp, date)
        bookingDao.insertDaySlot(daySlot, createTimeSlots(dayCapacity, timeCapacity, daySlot.date))
    }
  }

  private def createTimeSlots(dayCapacity: Int, timeCapacity: Int, date: Date): Seq[TimeSlotsRow] = {
    val bookingSlotTimestamp = date.toSqlTimestamp
    0.until(dayCapacity).map { index =>
      val startHour = TimeSlotsStartHour + index
      TimeSlotsRow(0, currentTimestamp, timeCapacity, 0, bookingSlotTimestamp.resetToHour(startHour).toSqlTime,
        bookingSlotTimestamp.resetToHour(startHour + 1).toSqlTime, 0)
    }
  }
}
