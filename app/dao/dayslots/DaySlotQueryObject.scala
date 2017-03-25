package dao.dayslots

import dao.QueryObject
import models.Tables._

import scala.language.higherKinds


class DaySlotQueryObject extends QueryObject[DaySlots, DaySlotsRow] {

  import profile.api._

  override def query: TableQuery[DaySlots] = DaySlots

  def insertQuery: profile.IntoInsertActionComposer[DaySlotsRow, DaySlotsRow] = {
    query returning query.map(_.id) into ((item, generatedId) => item.copy(id = generatedId))
  }

  def withTimeSlots: Query[(DaySlots, TimeSlots), (DaySlotsRow, TimeSlotsRow), Seq] = query.withTimeSlots

  implicit class DaySlotExtension[R[_]](query: Query[DaySlots, DaySlotsRow, R]) {
    def withTimeSlots: Query[(DaySlots, TimeSlots), (DaySlotsRow, TimeSlotsRow), R] =
      query.join(TimeSlots).on(_.id === _.daySlotId)
  }

}
