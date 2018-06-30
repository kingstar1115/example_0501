package services.internal.bookings

import java.sql.Date
import java.time.{LocalDate, LocalDateTime}

import commons.ServerError
import commons.SettingConstants.Booking._
import commons.enums.ValidationError
import commons.monads.transformers.OptionT
import commons.utils.TimeUtils._
import dao.SlickDbService
import dao.countries.CountryDao
import dao.countries.CountryDao.{Country, CountryWithZipCodes}
import dao.dayslots.BookingDao.BookingSlot
import dao.dayslots._
import javax.inject.Inject
import models.Tables._
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import services.internal.bookings.DefaultBookingService.{Capacity, CountryDaySlots, DaySlotWithTimeSlots}
import services.internal.settings.SettingsService

import scala.concurrent.Future


class DefaultBookingService @Inject()(bookingDao: BookingDao,
                                      countryDao: CountryDao,
                                      dbService: SlickDbService,
                                      settingsService: SettingsService) extends BookingService {

  private val logger = Logger(this.getClass)

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

  @deprecated(message = "Use getBookingSlotsByCountries", since = "v4")
  override def getBookingSlots(startDate: LocalDate = LocalDate.now(),
                               endDate: LocalDate = LocalDate.now().plusDays(14)): Future[Seq[BookingSlot]] = {
    dbService.run {
      for {
        defaultCountry <- countryDao.getDefaultCountry
        countryDaySlots <- bookingDao.findDaySlotsForCountries(Set(defaultCountry.id), startDate.toSqlDate, endDate.toSqlDate)
      } yield countryDaySlots
    }.map { bookingSlots =>
      bookingSlots
        .map(bookingSlot => filterTimeSlots(bookingSlot))
        .filterNot(_.timeSlots.isEmpty)
    }
  }

  override def getBookingSlotsByCountries(ids: Set[Int] = Set.empty,
                                          startDate: LocalDate = LocalDate.now(),
                                          endDate: LocalDate = LocalDate.now().plusDays(14)): Future[Seq[CountryDaySlots]] = {
    dbService.run {
      (for {
        countries <- countryDao.getCountriesWithZipCodes(ids)
        daySlots <- bookingDao.findDaySlotsForCountries(countries.map(_.country.id).toSet, startDate.toSqlDate, endDate.toSqlDate)
      } yield (countries, daySlots)).map {
        case (countries, daySlots) =>
          countries.map(countryWithZipCodes => {
            val countryDaySlots = daySlots.filter(_.daySlot.countryId == countryWithZipCodes.country.id)
              .map(bookingSlot => filterTimeSlots(bookingSlot))
              .filterNot(_.timeSlots.isEmpty)
            CountryDaySlots(countryWithZipCodes, countryDaySlots)
          })
      }
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
    dbService.findOneOption(TimeSlotQueryObject.findByIdQuery(id))
  }

  override def increaseCapacity(id: Int, newCapacity: Int): Future[Either[ServerError, TimeSlotsRow]] = {
    findTimeSlot(id).flatMap {
      case Some(timeSlot) if timeSlot.reserved <= newCapacity =>
        val updatedTimeSlot = timeSlot.copy(capacity = newCapacity)
        dbService.run(TimeSlotQueryObject.updateQuery(updatedTimeSlot))
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

  override def createDaySlotWithTimeSlots(dates: Set[Date]): Future[Seq[DaySlotWithTimeSlots]] = {
    (for {
      numberOfTimeSlotsPerDay <- settingsService.getIntValue(DaySlotCapacity, DefaultDaySlotCapacity)
      timeSlotCapacity <- settingsService.getIntValue(TimeSlotCapacity, DefaultTimeSlotCapacity)
    } yield Capacity(numberOfTimeSlotsPerDay, timeSlotCapacity)).flatMap { implicit capacity =>
      dbService.run {
        for {
          countries <- countryDao.getAllCountries
          daySlots <- bookingDao.findByDates(dates)
          createdDaySlots <- createMissingDaySlots(dates, countries, daySlots)
        } yield createdDaySlots
      }
    }
  }

  private def createMissingDaySlots(dates: Set[Date], countries: Seq[Country], existingDaySlots: Seq[DaySlotsRow])
                                   (implicit capacity: Capacity) = {
    val newDaySlots = countries.flatMap(country => {
      val countryDaySlots = existingDaySlots.filter(daySlot => daySlot.countryId == country.id)
      dates.filter(date => !countryDaySlots.exists(_.date == date))
        .map { date =>
          logger.info(s"Creating booking slot for '$date' for country '${country.name}'")
          val daySlot = DaySlotsRow(0, currentTimestamp, date, country.id)
          val timeSlots = createTimeSlots(date)
          DaySlotWithTimeSlots(daySlot, timeSlots)
        }
    })
    bookingDao.createDaySlots(newDaySlots)
  }

  private def createTimeSlots(date: Date)(implicit capacity: Capacity): List[TimeSlotsRow] = {
    val bookingSlotTimestamp = date.toSqlTimestamp
    0.until(capacity.timeSlotsPerDay).map { index =>
      val startHour = TimeSlotsStartHour + index
      TimeSlotsRow(0, currentTimestamp, capacity.timeSlotCapacity, 0, bookingSlotTimestamp.resetToHour(startHour).toSqlTime,
        bookingSlotTimestamp.resetToHour(startHour + 1).toSqlTime, 0)
    }.toList
  }

  override def getBookingTime(timeSlotId: Int): Future[Option[LocalDateTime]] = {
    dbService.run(bookingDao.findTimeSlot(timeSlotId)).map {
      case Some((timeSlot, daySlot)) =>
        Some(LocalDateTime.of(daySlot.date.toLocalDate, timeSlot.startTime.toLocalTime))
    }
  }
}

object DefaultBookingService {

  case class Capacity(timeSlotsPerDay: Int, timeSlotCapacity: Int)

  case class DaySlotWithTimeSlots(daySlot: DaySlotsRow, timeSlots: Seq[TimeSlotsRow])

  case class CountryDaySlots(country: CountryWithZipCodes, bookingSlots: List[BookingSlot])

}
