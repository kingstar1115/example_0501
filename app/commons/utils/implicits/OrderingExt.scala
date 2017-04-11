package commons.utils.implicits

import java.sql.Date

import scala.math.Ordering


object OrderingExt {

  trait DateOrdering extends Ordering[Date] {
    def compare(x: Date, y: Date): Int = x.compareTo(y)
  }

  implicit object DateOrdering extends DateOrdering

}