package dao.dayslots

import java.sql.{Date, Time}

import com.google.inject.ImplementedBy
import dao.dayslots.BookingDao.BookingSlot
import models.Tables._
import services.internal.bookings.DefaultBookingService.DaySlotWithTimeSlots
import slick.dbio.Effect.Read
import slick.dbio.{DBIOAction, Effect, NoStream, StreamingDBIO}

import scala.concurrent.Future

@ImplementedBy(classOf[SlickBookingDao])
trait BookingDao {

  def findByDates(dates: Set[Date]): StreamingDBIO[Seq[DaySlotsRow], DaySlotsRow]

  def findByDateWithTimeSlots(date: Date): Future[Option[(DaySlotsRow, Seq[TimeSlotsRow])]]

  def findFreeTimeSlotByDateTime(date: Date, time: Time): Future[Option[TimeSlotsRow]]

  def increaseBooking(timeSlot: TimeSlotsRow): Future[Int]

  def decreaseBooking(timeSlot: TimeSlotsRow): Future[TimeSlotsRow]

  def findBookingSlots(startDate: Date, endDate: Date): Future[Seq[BookingSlot]]

  def findDaySlotsForCountries(countries: Set[Int], startDate: Date, endDate: Date)
  : DBIOAction[List[BookingSlot], NoStream, Read]

  def hasBookingSlotsAfterDate(date: Date): Future[Boolean]

  def createDaySlots(daySlots: Seq[DaySlotWithTimeSlots]): DBIOAction[Seq[DaySlotWithTimeSlots], NoStream, Effect.Write with Effect.Transactional]
}

object BookingDao {

  case class BookingSlot(daySlot: DaySlotsRow, timeSlots: Seq[TimeSlotsRow])

}