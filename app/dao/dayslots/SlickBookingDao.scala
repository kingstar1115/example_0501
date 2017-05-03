package dao.dayslots

import java.sql.{Date, Time}
import javax.inject.Inject

import commons.utils.implicits.OrderingExt._
import dao.dayslots.BookingDao.BookingSlot
import models.Tables
import models.Tables._
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits._
import slick.driver.JdbcProfile

import scala.concurrent.Future
import scala.util.{Failure, Success}

class SlickBookingDao @Inject()(val dbConfigProvider: DatabaseConfigProvider)
  extends BookingDao with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  override def query: TableQuery[DaySlots] = DaySlots

  private val daySlotQueryObject = DaySlotQueryObject
  private val timeSlotQueryObject = TimeSlotQueryObject

  override def findByDates(dates: Set[Date]): Future[Seq[DaySlotsRow]] = {
    val daySlotQuery = daySlotQueryObject.filter(_.date inSet dates).sortBy(_.date.asc)
    db.run(daySlotQuery.result)
  }

  override def findByDateWithTimeSlots(date: Date): Future[Option[(DaySlotsRow, Seq[TimeSlotsRow])]] = {
    val daySlotsWithTimeSlotsQuery = daySlotQueryObject.withTimeSlots.filter(_._1.date === date)
    db.run(daySlotsWithTimeSlotsQuery.result).map { resultSet =>
      resultSet.groupBy(_._1).headOption
        .map(tuple => (tuple._1, tuple._2.map(_._2)))
    }
  }

  override def findFreeTimeSlotByDateTime(date: Date, time: Time): Future[Option[TimeSlotsRow]] = {
    val freeTimeSlotQuery = daySlotQueryObject.withTimeSlots
      .filter(pair => pair._1.date === date && pair._2.startTime === time && pair._2.reserved < pair._2.capacity)
      .map(_._2)
    db.run(freeTimeSlotQuery.result.headOption)
  }

  def increaseBooking(timeSlot: TimeSlotsRow): Future[Int] = {
    val bookingQuery =
      sql"""UPDATE time_slots SET reserved = reserved + 1
          WHERE id = ${timeSlot.id} AND capacity > reserved""".as[Int]
    db.run(bookingQuery.head)
  }

  def decreaseBooking(timeSlot: TimeSlotsRow): Future[TimeSlotsRow] = {
    val dBIOAction = (for {
      _ <- sql"""UPDATE time_slots SET reserved = reserved - 1
          WHERE id = ${timeSlot.id} AND reserved - 1 >= 0""".as[Int]
      updatedTimeSlot <- timeSlotQueryObject.findByIdQuery(timeSlot.id).result.head
    } yield updatedTimeSlot).transactionally
    db.run(dBIOAction)
  }

  override def findBookingSlots(startDate: Date, endDate: Date): Future[Seq[BookingSlot]] = {
    val bookingSlotsQuery = daySlotQueryObject.withTimeSlots
      .filter(pair => pair._1.date >= startDate && pair._1.date <= endDate)
    db.run(bookingSlotsQuery.result).map { resultSet =>
      resultSet.groupBy(_._1)
        .map(entry => BookingSlot(entry._1, entry._2.map(_._2).sortBy(_.startTime))).toSeq.sortBy(_.daySlot.date)
    }
  }

  override def hasBookingSlotsAfterDate(date: Date): Future[Boolean] = {
    db.run(daySlotQueryObject.filter(_.date >= date).length.result)
      .map(_ > 0)
  }

  override def insertDaySlot(daySlot: Tables.DaySlotsRow, timeSlots: Seq[Tables.TimeSlotsRow]): Future[(Tables.DaySlotsRow, Seq[Tables.TimeSlotsRow])] = {
    val insertAction = (for {
      savedDaySlot <- daySlotQueryObject.insertQuery += daySlot
      savedTimeSlots <- timeSlotQueryObject.insertQuery ++= timeSlots.map(timeSlot => timeSlot.copy(daySlotId = savedDaySlot.id))
    } yield (savedDaySlot, savedTimeSlots)).transactionally
    val insertFuture = db.run(insertAction)
    insertFuture.onComplete {
      case Success((savedDaySlot, _)) =>
        Logger.info(s"Successfully created day slot for '${savedDaySlot.date}' with id: ${savedDaySlot.id}")
      case Failure(e) =>
        Logger.info(s"Failed to save day slot for '${daySlot.date}'", e)
    }
    insertFuture
  }
}

