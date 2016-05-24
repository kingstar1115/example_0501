package commons.enums


object TaskStatuses {

  sealed abstract class TaskStatus(val code: Int)

  case object Assigned extends TaskStatus(0)

  case object Started extends TaskStatus(1)

  case object Successful extends TaskStatus(2)

  case object Failed extends TaskStatus(3)

  case object InProgress extends TaskStatus(4)

  case object Unassigned extends TaskStatus(6)

  case object Accepted extends TaskStatus(7)

  case object Decline extends TaskStatus(8)

  case object Cancel extends TaskStatus(9)

  lazy val activeStatuses = Set(Accepted.code, Started.code, InProgress.code)

}
