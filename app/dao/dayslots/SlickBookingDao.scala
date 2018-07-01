package dao.dayslots

import java.sql.{Date, Time}

import commons.utils.implicits.OrderingExt._
import dao.countries.CountryDao
import dao.dayslots.BookingDao.BookingSlot
import javax.inject.Inject
import models.Tables._
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits._
import services.internal.bookings.DefaultBookingService.DaySlotWithTimeSlots
import slick.dbio.Effect.Read
import slick.driver.JdbcProfile

import scala.concurrent.Future

class SlickBookingDao @Inject()(val dbConfigProvider: DatabaseConfigProvider,
                                countryDao: CountryDao) extends BookingDao with HasDatabaseConfigProvider[JdbcProfile] {

  val logger = Logger(this.getClass)

  import driver.api._

  private val daySlotQueryObject = DaySlotQueryObject
  private val timeSlotQueryObject = TimeSlotQueryObject

  override def findByDates(dates: Set[Date]): StreamingDBIO[Seq[DaySlotsRow], DaySlotsRow] = {
    daySlotQueryObject.filter(_.date.inSet(dates)).result
  }

  override def findByDateWithTimeSlots(date: Date): Future[Option[(DaySlotsRow, Seq[TimeSlotsRow])]] = {
    val daySlotsWithTimeSlotsQuery = daySlotQueryObject.withTimeSlots.filter(_._1.date === date)
    db.run(daySlotsWithTimeSlotsQuery.result).map { resultSet =>
      resultSet.groupBy(_._1).headOption
        .map(tuple => (tuple._1, tuple._2.map(_._2)))
    }
  }

  override def findFreeTimeSlotByDateTime(date: Date, time: Time): Future[Option[TimeSlotsRow]] = {
    val timeSlotDBIAction = for {
      country <- countryDao.getDefaultCountry
      timeSlot <- daySlotQueryObject.withTimeSlots
        .filter {
          case (daySlotRow, timeSlotRow) => (daySlotRow.countryId === country.id && daySlotRow.date === date
            && timeSlotRow.startTime === time && timeSlotRow.reserved < timeSlotRow.capacity)
        }
        .map(_._2)
        .result.headOption
    } yield timeSlot
    db.run(timeSlotDBIAction)
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

  override def findDaySlotsForCountries(countries: Set[Int], startDate: Date, endDate: Date)
  : DBIOAction[List[BookingSlot], NoStream, Read] = {
    daySlotQueryObject
      .filter(daySlot => daySlot.countryId.inSet(countries) && daySlot.date >= startDate && daySlot.date <= endDate)
      .join(TimeSlots).on(_.id === _.daySlotId).result
      .map(_.groupBy(_._1.id).map({
        case (_, daySlotTuples) =>
          val (daySlot, timeSlot) = daySlotTuples.head
          val timeSlots = List(timeSlot) ::: daySlotTuples.tail.map(_._2).toList
          BookingSlot(daySlot, timeSlots.sortBy(_.startTime))
      }).toList.sortBy(_.daySlot.date))
  }

  override def hasBookingSlotsAfterDate(date: Date): Future[Boolean] = {
    db.run(daySlotQueryObject.filter(_.date >= date).length.result)
      .map(_ > 0)
  }

  override def createDaySlots(daySlotsWithTimeSlots: Seq[DaySlotWithTimeSlots]): DBIOAction[Seq[DaySlotWithTimeSlots], NoStream, Effect.Write with Effect.Transactional] = {
    DBIO.sequence {
      daySlotsWithTimeSlots.map { daySlotWithTimeSlots =>
        (for {
          savedDaySlot <- daySlotQueryObject.insertQuery += daySlotWithTimeSlots.daySlot
          savedTimeSlots <- timeSlotQueryObject.insertQuery ++= daySlotWithTimeSlots.timeSlots
            .map(timeSlot => timeSlot.copy(daySlotId = savedDaySlot.id))
        } yield DaySlotWithTimeSlots(savedDaySlot, savedTimeSlots)).map { savedEntity =>
          logger.info(s"Successfully created day slot for '(${savedEntity.daySlot.date}, ${savedEntity.daySlot.countryId})'")
          savedEntity
        }
      }
    }.transactionally
  }

  override def findTimeSlot(timeSlotId: Int): DBIOAction[Option[(TimeSlotsRow, DaySlotsRow)], NoStream, Effect.Read] = {
    TimeSlotQueryObject.filter(_.id === timeSlotId)
      .join(DaySlotQueryObject.query).on(_.daySlotId === _.id)
      .result.headOption
  }
}

