package dto

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import dao.dayslots.BookingDao.BookingSlot
import models.Tables.TimeSlotsRow
import services.internal.bookings.DefaultBookingService.CountryDaySlots


object BookingDto {

  case class CountryDto(id: Int, name: String, daySlots: Seq[DaySlotDto])

  object CountryDto {
    def apply(countryWithDaySlots: CountryDaySlots): CountryDto = {
      val country = countryWithDaySlots.country.country
      new CountryDto(country.id, country.name, DaySlotDto.fromBookingSlots(countryWithDaySlots.bookingSlots))
    }
  }

  case class DaySlotDto(id: Int, date: LocalDate, dateStr: String, timeSlots: Seq[TimeSlotDto])

  object DaySlotDto {

    private val LocalDateFormatter = DateTimeFormatter.ofPattern("yyyy MMM EE dd")

    def apply(bookingSlot: BookingSlot): DaySlotDto = {
      val timeSlots = bookingSlot.timeSlots.map(TimeSlotDto.apply)
      new DaySlotDto(bookingSlot.daySlot.id, bookingSlot.daySlot.date.toLocalDate,
        LocalDateFormatter.format(bookingSlot.daySlot.date.toLocalDate), timeSlots)
    }

    def fromBookingSlots(bookingSlot: Seq[BookingSlot]): Seq[DaySlotDto] = {
      bookingSlot.map(bookingSlot => DaySlotDto(bookingSlot))
    }
  }

  case class TimeSlotDto(id: Int, startTime: String, endTime: String, capacity: Int, reserved: Int)

  object TimeSlotDto {

    private val LocalTimeFormatter = DateTimeFormatter.ofPattern("hha:mm")

    def apply(timeSlotsRow: TimeSlotsRow): TimeSlotDto = {
      TimeSlotDto(timeSlotsRow.id, LocalTimeFormatter.format(timeSlotsRow.startTime.toLocalTime),
        LocalTimeFormatter.format(timeSlotsRow.endTime.toLocalTime), timeSlotsRow.capacity, timeSlotsRow.reserved)
    }
  }

}
