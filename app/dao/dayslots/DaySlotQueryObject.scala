package dao.dayslots

import dao.QueryObject
import models.Tables._

object DaySlotQueryObject extends QueryObject[DaySlots, DaySlotsRow] {

  import profile.api._

  override def query: TableQuery[DaySlots] = DaySlots

  def insertQuery: profile.IntoInsertActionComposer[DaySlotsRow, DaySlotsRow] = {
    query returning query.map(_.id) into ((item, generatedId) => item.copy(id = generatedId))
  }

  def withTimeSlots: Query[(DaySlots, TimeSlots), (DaySlotsRow, TimeSlotsRow), Seq] = query.join(TimeSlots).on(_.id === _.daySlotId)

}
