package controllers

import com.google.inject.Inject
import controllers.base.BaseController
import dto.BookingDtos.BookingDayDto
import security.TokenStorage
import services.internal.bookings.BookingService

import scala.concurrent.ExecutionContext.Implicits.global


class BookingsController @Inject()(val tokenStorage: TokenStorage,
                                   bookingService: BookingService) extends BaseController {

  def getBookingSlots(version: String) = authorized.async { _ =>
    bookingService.getBookingSlots
      .map(BookingDayDto.fromBookingSlots)
      .map(bookingDays => ok(bookingDays))
  }

}
