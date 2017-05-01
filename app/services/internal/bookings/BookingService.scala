package services.internal.bookings

import java.time.{LocalDate, LocalDateTime}

import com.google.inject.ImplementedBy
import commons.ServerError
import dao.dayslots.BookingDao.BookingSlot
import models.Tables._

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultBookingService])
trait BookingService {

  def findFreeTimeSlot(dataTime: LocalDateTime): Future[Option[TimeSlotsRow]]

  def reserveBooking(dateTime: LocalDateTime): Future[Option[TimeSlotsRow]]

  def releaseBooking(timeSlot: TimeSlotsRow): Future[TimeSlotsRow]

  def getBookingSlots(startDate: LocalDate = LocalDate.now(),
                      endDate: LocalDate = LocalDate.now().plusDays(14)): Future[Seq[BookingSlot]]

  def findTimeSlot(id: Int): Future[Option[TimeSlotsRow]]

  def increaseCapacity(id: Int, capacity: Int): Future[Either[ServerError, TimeSlotsRow]]

  def hasBookingSlotsAfterDate(date: LocalDate): Future[Boolean]
}
