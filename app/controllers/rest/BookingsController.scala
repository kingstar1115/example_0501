package controllers.rest

import com.google.inject.Inject
import controllers.rest.base._
import dto.rest.BookingDtos.BookingDayDto
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Action
import security.TokenStorage
import services.internal.bookings.BookingService

//noinspection TypeAnnotation
class BookingsController @Inject()(val tokenStorage: TokenStorage,
                                   bookingService: BookingService) extends BaseController {

  def getBookingSlots(version: String) = Action.async { _ =>
    bookingService.getBookingSlots()
      .map(BookingDayDto.fromBookingSlots)
      .map(bookingDays => ok(bookingDays))
  }

}
