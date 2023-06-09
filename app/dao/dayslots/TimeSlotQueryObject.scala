package dao.dayslots

import dao.QueryObject
import models.Tables._

object TimeSlotQueryObject extends QueryObject[TimeSlots, TimeSlotsRow] {

  import profile.api._

  override def query: TableQuery[TimeSlots] = TimeSlots

  def insertQuery: profile.IntoInsertActionComposer[TimeSlotsRow, TimeSlotsRow] = {
    query returning query.map(_.id) into ((item, generatedId) => item.copy(id = generatedId))
  }

  def findById(id: Int)  =
    (for {
      timeSlot <- query if timeSlot.id === id
      daySlot <- DaySlots if daySlot.id === timeSlot.daySlotId
    } yield (timeSlot, daySlot)).result.headOption
}
