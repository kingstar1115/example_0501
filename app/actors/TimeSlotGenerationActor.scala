package actors

import java.sql.Date

import actors.TimeSlotGenerationActor.GenerateTimeSlots
import akka.actor.Actor
import commons.utils.TimeUtils._
import javax.inject.Inject
import models.Tables.DaySlotsRow
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import services.internal.dayslots.DaySlotsService


class TimeSlotGenerationActor @Inject()(daySlotsService: DaySlotsService) extends Actor {

  private val logger = Logger(this.getClass)

  override def receive: Receive = {
    case GenerateTimeSlots =>
      generateTimeSlots

    case _ =>
  }

  private def generateTimeSlots = {
    logger.info(s"Generating booking slots $currentDate - ${currentDate.addDays(13)}")
    val bookingSlotDays = getBookingSlotDays
    daySlotsService.findByDates(bookingSlotDays)
      .map(createMissingBookingSlots(_, bookingSlotDays))
  }

  private def getBookingSlotDays: Set[Date] = {
    val now = currentDate
    (0 to 13 map { index =>
      now.addDays(index)
    }).toSet
  }

  private def createMissingBookingSlots(bookingSlots: Seq[DaySlotsRow], dates: Set[Date]): Unit = {
    val existingDates = bookingSlots.map(_.date)
    dates.filter(date => !existingDates.contains(date))
      .foreach(daySlotsService.createDaySlotWithTimeSlots)
  }

}

object TimeSlotGenerationActor {

  case object GenerateTimeSlots

}
