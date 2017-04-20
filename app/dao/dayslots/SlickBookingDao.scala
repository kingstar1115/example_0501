package dao.dayslots

import java.sql.{Date, Time}
import javax.inject.Inject

import commons.utils.implicits.OrderingExt._
import dao.SlickDriver
import dao.dayslots.BookingDao.BookingSlot
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SlickBookingDao @Inject()(val dbConfigProvider: DatabaseConfigProvider) extends BookingDao with SlickDriver {

  import profile.api._

  override def query: TableQuery[DaySlots] = DaySlots

  private val daySlotQueryObject = DaySlotQueryObject
  private val timeSlotQueryObject = TimeSlotQueryObject

  override def findByDates(dates: Set[Date]): Future[Seq[DaySlotsRow]] = {
    val daySlotQuery = daySlotQueryObject.filter(_.date inSet dates).sortBy(_.date.asc)
    run(daySlotQuery.result)
  }

  override def findByDateWithTimeSlots(date: Date): Future[Option[(DaySlotsRow, Seq[TimeSlotsRow])]] = {
    val daySlotsWithTimeSlotsQuery = daySlotQueryObject.withTimeSlots.filter(_._1.date === date)
    run(daySlotsWithTimeSlotsQuery.result).map { resultSet =>
      resultSet.groupBy(_._1).headOption
        .map(tuple => (tuple._1, tuple._2.map(_._2)))
    }
  }

  override def findFreeTimeSlotByDateTime(date: Date, time: Time): Future[Option[TimeSlotsRow]] = {
    val freeTimeSlotQuery = daySlotQueryObject.withTimeSlots
      .filter(pair => pair._1.date === date && pair._2.startTime === time && pair._2.bookingsCount < pair._2.capacity)
      .map(_._2)
    run(freeTimeSlotQuery.result.headOption)
  }

  def increaseBooking(timeSlot: TimeSlotsRow): Future[Int] = {
    val bookingQuery =
      sql"""UPDATE time_slots SET bookings_count = bookings_count + 1
          WHERE id = ${timeSlot.id} AND capacity > bookings_count""".as[Int]
    run(bookingQuery.head)
  }

  def decreaseBooking(timeSlot: TimeSlotsRow): Future[TimeSlotsRow] = {
    val dBIOAction = (for {
      _ <- sql"""UPDATE time_slots SET bookings_count = bookings_count - 1
          WHERE id = ${timeSlot.id} AND bookings_count - 1 >= 0""".as[Int]
      updatedTimeSlot <- timeSlotQueryObject.findByIdQuery(timeSlot.id).result.head
    } yield updatedTimeSlot).transactionally
    run(dBIOAction)
  }

  override def findBookingSlots(startDate: Date, endDate: Date): Future[Seq[BookingSlot]] = {
    val bookingSlotsQuery = daySlotQueryObject.withTimeSlots
      .filter(pair => pair._1.date >= startDate && pair._1.date <= endDate)
    run(bookingSlotsQuery.result).map { resultSet =>
      resultSet.groupBy(_._1)
        .map(entry => BookingSlot(entry._1, entry._2.map(_._2))).toSeq.sortBy(_.daySlot.date)
    }
  }
}

