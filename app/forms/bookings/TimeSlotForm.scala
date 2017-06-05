package forms.bookings

import play.api.data.Form
import play.api.data.Forms._


case class TimeSlotForm(id: Int, date: String, startTime: String, endTime: String, capacity: Int, reserved: Int) {
  def wrapToForm(): Form[TimeSlotForm] = TimeSlotForm.form().fill(this)
}

object TimeSlotForm {
  val Id = "id"
  val Date = "date"
  val StartTime = "startTime"
  val EndTime = "endTime"
  val Capacity = "capacity"
  val Reserved = "reserved"

  def form() = Form(
    mapping(
      Id -> number,
      Date -> text,
      StartTime -> text,
      EndTime -> text,
      Capacity -> number(min = 0),
      Reserved -> number
    )(TimeSlotForm.apply)(TimeSlotForm.unapply)
  )
}
