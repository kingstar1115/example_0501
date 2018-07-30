package actors

import java.sql.Date

import actors.TimeSlotGenerationActor.GenerateTimeSlots
import akka.actor.Actor
import commons.utils.TimeUtils._
import javax.inject.Inject
import play.api.Logger
import services.internal.bookings.BookingService

class TimeSlotGenerationActor @Inject()(bookingService: BookingService) extends Actor {

  private val logger = Logger(this.getClass)

  override def receive: Receive = {
    case GenerateTimeSlots =>
      generateTimeSlots
    case _ =>
  }

  private def generateTimeSlots = {
    logger.info(s"Generating booking slots $currentDate - ${currentDate.addDays(13)}")
    bookingService.createDaySlotWithTimeSlots(getBookingSlotDays)
  }

  private def getBookingSlotDays: Set[Date] = {
    val now = currentDate
    (0 to 13 map { index =>
      now.addDays(index)
    }).toSet
  }

}

object TimeSlotGenerationActor {

  case object GenerateTimeSlots

}
