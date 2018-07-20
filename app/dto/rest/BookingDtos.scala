package dto.rest

import java.sql.Date
import java.time.{LocalDate, ZoneId}

import commons.utils.implicits.WritesExt._
import dao.countries.CountryDao.CountryWithZipCodes
import dao.dayslots.BookingDao.BookingSlot
import models.Tables._
import play.api.libs.json._
import services.internal.bookings.DefaultBookingService.CountryDaySlots

object BookingDtos {

  case class CountryBookingsDto(id: Int, name: String, code: String, zipCodes: Set[String], daySlots: Set[BookingDayDto])

  object CountryBookingsDto {

    def convert(countryDaySlot: CountryDaySlots): CountryBookingsDto = {
      val CountryWithZipCodes(country, zipCodes) = countryDaySlot.country
      new CountryBookingsDto(country.id, country.name, country.code, zipCodes.map(_.zipCode),
        BookingDayDto.fromBookingSlots(countryDaySlot.bookingSlots).toSet)
    }

    def toJson(countryBookingsDtos: Seq[CountryBookingsDto]): JsValue = {
      val tuples = countryBookingsDtos.map(countryBookingSlots => {
        countryBookingSlots.code -> JsObject(
          Seq(
            "id" -> JsNumber(countryBookingSlots.id),
            "name" -> JsString(countryBookingSlots.name),
            "zipCodes" -> Json.toJson(countryBookingSlots.zipCodes),
            "daySlots" -> Json.toJson(countryBookingSlots.daySlots)
          )
        )
      })
      JsObject(tuples)
    }
  }

  case class BookingDayDto(id: Int, date: LocalDate, isCurrentDate: Boolean, bookingSlots: Seq[BookingSlotDto])

  object BookingDayDto {

    import BookingSlotDto._

    implicit val bookingDayWrites: Writes[BookingDayDto] = Json.writes[BookingDayDto]

    private def convert(daySlot: DaySlotsRow, bookingSlots: Seq[BookingSlotDto]): BookingDayDto = {
      val date = daySlot.date.toLocalDate
      val isCurrentDate = date.equals(LocalDate.now())
      BookingDayDto(daySlot.id, date, isCurrentDate, bookingSlots)
    }

    def fromBookingSlot(bookingSlot: BookingSlot): BookingDayDto = {
      val bookingSlots = bookingSlot.timeSlots.map(_.convert(bookingSlot.daySlot.date))
      convert(bookingSlot.daySlot, bookingSlots)
    }

    def fromBookingSlots(bookingSlots: Seq[BookingSlot]): Seq[BookingDayDto] = {
      bookingSlots.map(fromBookingSlot)
    }
  }

  case class BookingSlotDto(id: Int, available: Boolean, startTime: Long, entTime: Long)

  object BookingSlotDto {

    private val DefaultZoneId = ZoneId.systemDefault()

    implicit val bookingSlotWrites: Writes[BookingSlotDto] = Json.writes[BookingSlotDto]

    implicit class TimeSlotToBookingSlotDtoConverter(timeSlot: TimeSlotsRow) {
      def convert(date: Date): BookingSlotDto = {
        val available = timeSlot.reserved < timeSlot.capacity
        val startDateTime = date.toLocalDate.atTime(timeSlot.startTime.toLocalTime).atZone(DefaultZoneId).toEpochSecond
        val endDateTime = date.toLocalDate.atTime(timeSlot.endTime.toLocalTime).atZone(DefaultZoneId).toEpochSecond
        BookingSlotDto(timeSlot.id, available, startDateTime, endDateTime)
      }
    }

  }

}
