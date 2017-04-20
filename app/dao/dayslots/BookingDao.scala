package dao.dayslots

import java.sql.{Date, Time}

import com.google.inject.ImplementedBy
import dao.EntityDao
import dao.dayslots.BookingDao.BookingSlot
import models.Tables._

import scala.concurrent.Future

@ImplementedBy(classOf[SlickBookingDao])
trait BookingDao extends EntityDao[DaySlots, DaySlotsRow] {

  def findByDates(dates: Set[Date]): Future[Seq[DaySlotsRow]]

  def findByDateWithTimeSlots(date: Date): Future[Option[(DaySlotsRow, Seq[TimeSlotsRow])]]

  def findFreeTimeSlotByDateTime(date: Date, time: Time): Future[Option[TimeSlotsRow]]

  def increaseBooking(timeSlot: TimeSlotsRow): Future[Int]

  def decreaseBooking(timeSlot: TimeSlotsRow): Future[(TimeSlotsRow)]

  def findBookingSlots(startDate: Date, endDate: Date): Future[Seq[BookingSlot]]
}

object BookingDao {

  case class BookingSlot(daySlot: DaySlotsRow, timeSlots: Seq[TimeSlotsRow])

}