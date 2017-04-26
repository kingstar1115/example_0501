package config

import java.time.LocalDate

import play.api.mvc.PathBindable

import scala.util._

object PathBinders {

  implicit object LocalDatePathBindable extends PathBindable[LocalDate] {
    override def bind(key: String, value: String): Either[String, LocalDate] = {
      Try(LocalDate.parse(value)) match {
        case Success(date) =>
          Right(date)
        case Failure(error) =>
          Left(s"Can't parse LocalDate. Message: ${error.getMessage}")
      }
    }

    override def unbind(key: String, value: LocalDate): String = {
      value.toString
    }
  }

}
