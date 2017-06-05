package config

import java.time.LocalDate

import play.api.mvc.{PathBindable, QueryStringBindable}

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

  implicit object LocalDateQueryStringBindable extends QueryStringBindable[LocalDate] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, LocalDate]] = {
      val value: Option[Seq[String]] = params.get(key)
      Try {
        value.map(_.head).map(str => LocalDate.parse(str))
      } match {
        case Success(Some(date)) => Some(Right(date))
        case Failure(error) => Some(Left(error.getMessage))
        case _ => None
      }
    }

    override def unbind(key: String, value: LocalDate): String = s"$key=${value.toString}"
  }

}
