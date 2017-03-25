package commons.utils.codegen

import java.sql.{Time, Timestamp, Date => SqlDate}
import java.util.Date


trait TimeUtils {

  def currentTimestamp(): Timestamp = new Timestamp(new Date().getTime)

  def currentTime(): Time = new Time(new Date().getTime)

  implicit class DateExt(date: SqlDate) {
    def toSqlTimestamp: Timestamp = Timestamp.valueOf(date.toLocalDate.atStartOfDay)
  }

  implicit class TimestampExt(timestamp: Timestamp) {

    def resetToHour(hour: Int): Timestamp = Timestamp.valueOf(timestamp.toLocalDateTime.toLocalDate.atTime(hour, 0))

    def toSqlTime: Time = Time.valueOf(timestamp.toLocalDateTime.toLocalTime)
  }

}
