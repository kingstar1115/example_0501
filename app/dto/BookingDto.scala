package dto

import java.time.format.DateTimeFormatter

import dao.dayslots.BookingDao.BookingSlot
import models.Tables.TimeSlotsRow


object BookingDto {

  case class TimeSlotDto(id: Int, startTime: String, endTime: String, capacity: Int, reserved: Int)

  object TimeSlotDto {

    private val LocalTimeFormatter = DateTimeFormatter.ofPattern("hha:mm")

    def fromTimeSlotRow(timeSlotsRow: TimeSlotsRow): TimeSlotDto = {
      TimeSlotDto(timeSlotsRow.id, LocalTimeFormatter.format(timeSlotsRow.startTime.toLocalTime),
        LocalTimeFormatter.format(timeSlotsRow.endTime.toLocalTime), timeSlotsRow.capacity, timeSlotsRow.reserved)
    }
  }

  case class DaySlotDto(id: Int, date: String, timeSlots: Seq[TimeSlotDto])

  object DaySlotDto {

    private val LocalDateFormatter = DateTimeFormatter.ofPattern("yyyy MMM EE dd")

    def fromBookingSlot(bookingSlot: BookingSlot): DaySlotDto = {
      val timeSlots = bookingSlot.timeSlots.map(TimeSlotDto.fromTimeSlotRow)
      DaySlotDto(bookingSlot.daySlot.id, LocalDateFormatter.format(bookingSlot.daySlot.date.toLocalDate), timeSlots)
    }

    def fromBookingSlots(bookingSlot: Seq[BookingSlot]): Seq[DaySlotDto] = {
      bookingSlot.map(fromBookingSlot)
    }
  }

}
