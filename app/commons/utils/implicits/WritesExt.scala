package commons.utils.implicits

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import play.api.libs.json.{DefaultWrites, Writes}


trait WritesExt extends DefaultWrites {

  import scala.language.implicitConversions

  implicit val LocalDateTimeWrites: Writes[LocalDateTime] =
    temporalWrites[LocalDateTime, DateTimeFormatter](DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"))

  implicit val LocalDateWrites: Writes[LocalDate] =
    temporalWrites[LocalDate, DateTimeFormatter](DateTimeFormatter.ofPattern("MM/dd/yyyy"))
}

object WritesExt extends WritesExt
