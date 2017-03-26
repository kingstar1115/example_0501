package services.internal.dayslots

import java.sql.Date

import com.google.inject.ImplementedBy
import models.Tables.{DaySlotsRow, TasksRow, TimeSlotsRow}

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultDaySlotService])
trait DaySlotsService {

  def findByDate(date: Date): Future[Option[(DaySlotsRow, Seq[TimeSlotsRow])]]

  def findByDates(dates: Set[Date]): Future[Seq[DaySlotsRow]]

  def createDaySlotWithTimeSlots(date: Date): Future[(DaySlotsRow, Seq[TimeSlotsRow])]

  def bookTimeSlot(task: TasksRow, timeSlot: TimeSlotsRow, skipCapacityValidation: Boolean = true): Future[Int]
}
