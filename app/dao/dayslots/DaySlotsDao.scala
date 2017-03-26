package dao.dayslots

import java.sql.Date

import com.google.inject.ImplementedBy
import dao.EntityDao
import models.Tables._

import scala.concurrent.Future

@ImplementedBy(classOf[SlickDaySlotsDao])
trait DaySlotsDao extends EntityDao[DaySlots, DaySlotsRow] {

  def findByDates(dates: Set[Date]): Future[Seq[DaySlotsRow]]

  def findByDateWithTimeSlots(date: Date): Future[Option[(DaySlotsRow, Seq[TimeSlotsRow])]]
}
