package dao.dayslots

import java.sql.{Date, Time}
import javax.inject.Inject

import commons.utils.implicits.OrderingExt._
import dao.dayslots.BookingDao.BookingSlot
import dao.{SlickDbService, SlickDriver}
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

class SlickBookingDao @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                                slickDbService: SlickDbService) extends BookingDao with SlickDriver {

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
      .filter(pair => pair._1.date === date && pair._2.startTime === time && pair._2.reserved < pair._2.capacity)
      .map(_._2)
    run(freeTimeSlotQuery.result.headOption)
  }

  def increaseBooking(timeSlot: TimeSlotsRow): Future[Int] = {
    val bookingQuery =
      sql"""UPDATE time_slots SET reserved = reserved + 1
          WHERE id = ${timeSlot.id} AND capacity > reserved""".as[Int]
    run(bookingQuery.head)
  }

  def decreaseBooking(timeSlot: TimeSlotsRow): Future[TimeSlotsRow] = {
    val dBIOAction = (for {
      _ <- sql"""UPDATE time_slots SET reserved = reserved - 1
          WHERE id = ${timeSlot.id} AND reserved - 1 >= 0""".as[Int]
      updatedTimeSlot <- timeSlotQueryObject.findByIdQuery(timeSlot.id).result.head
    } yield updatedTimeSlot).transactionally
    slickDbService.run(dBIOAction)
  }

  override def findBookingSlots(startDate: Date, endDate: Date): Future[Seq[BookingSlot]] = {
    val bookingSlotsQuery = daySlotQueryObject.withTimeSlots
      .filter(pair => pair._1.date >= startDate && pair._1.date <= endDate)
    slickDbService.run(bookingSlotsQuery.result).map { resultSet =>
      resultSet.groupBy(_._1)
        .map(entry => BookingSlot(entry._1, entry._2.map(_._2).sortBy(_.startTime))).toSeq.sortBy(_.daySlot.date)
    }
  }

  override def hasBookingSlotsAfterDate(date: Date): Future[Boolean] = {
    slickDbService.run(daySlotQueryObject.filter(_.date >= date).length.result)
      .map(_ > 0)
  }
}

