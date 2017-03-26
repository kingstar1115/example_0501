package dao.dayslots

import java.sql.Date
import javax.inject.Inject

import dao.SlickDriver
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SlickDaySlotsDao @Inject()(val dbConfigProvider: DatabaseConfigProvider) extends DaySlotsDao with SlickDriver {

  import profile.api._

  override def query: TableQuery[DaySlots] = DaySlots

  private val daySlotQueryObject = new DaySlotQueryObject

  override def findByDates(dates: Set[Date]) = {
    val daySlotQuery = daySlotQueryObject.filter(_.date inSet dates).sortBy(_.date.asc)
    run(daySlotQuery.result)
  }

  override def findByDateWithTimeSlots(date: Date): Future[Option[(DaySlotsRow, Seq[TimeSlotsRow])]] = {
    val daySlotsWithTimeSlotsQuery = daySlotQueryObject.withTimeSlots.filter(_._1.date === date)
    run(daySlotsWithTimeSlotsQuery.result).map { resultSet =>
      resultSet.groupBy(_._1).headOption
        .map(tuple => (tuple._1, tuple._2.map(_._2)))
    }
  }
}

