package controllers.rest

import com.google.inject.Inject
import controllers.rest.base._
import dto.rest.BookingDtos._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc.Action
import security.TokenStorage
import services.internal.bookings.BookingService

import scala.concurrent.Future

//noinspection TypeAnnotation
class BookingsController @Inject()(val tokenStorage: TokenStorage,
                                   bookingService: BookingService) extends BaseController {

  def getBookingSlots(version: String) = Action.async { _ =>
    version match {
      case "v3" =>
        bookingService.getBookingSlots()
          .map(BookingDayDto.fromBookingSlots)
          .map(bookingDays => ok(bookingDays))
      case "v4" =>
        bookingService.getBookingSlotsByCountries()
          .map(_.map(CountryBookingsDto.convert))
          .map(bookingSlotsByCountries => ok(bookingSlotsByCountries))
      case _ =>
        Future.successful(badRequest(s"Unsupported API version $version for '/booking-slots'"))
    }
  }
}
