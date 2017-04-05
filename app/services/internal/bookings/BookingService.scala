package services.internal.bookings

import java.time.LocalDateTime

import com.google.inject.ImplementedBy
import models.Tables._

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultBookingService])
trait BookingService {

  def findFreeTimeSlotByTimestamp(dataTime: LocalDateTime): Future[Option[TimeSlotsRow]]

  def reserveBooking(dateTime: LocalDateTime): Future[Option[TimeSlotsRow]]

  def releaseBooking(timeSlot: TimeSlotsRow): Future[TimeSlotsRow]
}
