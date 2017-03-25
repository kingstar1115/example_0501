package dao.dayslots

import dao.QueryObject
import models.Tables._

class TimeSlotQueryObject extends QueryObject[TimeSlots, TimeSlotsRow] {

  import profile.api._

  override def query: profile.api.TableQuery[TimeSlots] = TimeSlots

  def insertQuery: profile.IntoInsertActionComposer[TimeSlotsRow, TimeSlotsRow] = {
    query returning query.map(_.id) into ((item, generatedId) => item.copy(id = generatedId))
  }

}
