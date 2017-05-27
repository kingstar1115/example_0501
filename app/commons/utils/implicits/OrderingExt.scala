package commons.utils.implicits

import java.sql.{Date, Time}

import scala.math.Ordering


object OrderingExt {
  
  implicit object SqlDateOrdering extends Ordering[Date] {
    override def compare(x: Date, y: Date): Int = x.compareTo(y)
  }

  implicit object SqlTimeOrdering extends Ordering[Time] {
    override def compare(x: Time, y: Time): Int = {
      x.compareTo(y)
    }
  }

}