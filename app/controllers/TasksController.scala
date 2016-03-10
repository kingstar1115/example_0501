package controllers

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import controllers.TasksController._
import controllers.base.BaseController
import play.api.Configuration
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{Reads, _}
import play.api.mvc.{Action, BodyParsers}
import security.TokenStorage
import services.TookanService

import scala.concurrent.ExecutionContext.Implicits.global


class TasksController @Inject()(val tokenStorage: TokenStorage,
                                tookanService: TookanService,
                                config: Configuration) extends BaseController {

  def createTask = Action.async(BodyParsers.parse.json) { request =>
    def onValidationSuccess(dto: TaskDto) = {
      tookanService.createAppointment(dto.pickupName, dto.pickupPhone, dto.pickupAddress,
        dto.description, dto.pickupDateTime, Some(dto.pickupLongitude), Some(dto.pickupLatitude), None)
        .map {
          case Left(error) => badRequest(error)
          case Right(task) => ok(task)
        }
    }

    request.body.validate[TaskDto]
      .fold(jsonValidationFailedFuture, onValidationSuccess)
  }
}

object TasksController {

  case class TaskDto(token: String,
                     description: String,
                     pickupName: String,
                     pickupEmail: Option[String],
                     pickupPhone: String,
                     pickupAddress: String,
                     pickupLatitude: Double,
                     pickupLongitude: Double,
                     pickupDateTime: LocalDateTime)

  val dateTimeReads = localDateTimeReads(DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"))
  implicit val taskDtoReads: Reads[TaskDto] = (
    (JsPath \ "token").read[String] and
      (JsPath \ "description").read[String] and
      (JsPath \ "name").read[String] and
      (JsPath \ "email").readNullable[String](email) and
      (JsPath \ "phone").read[String] and
      (JsPath \ "address").read[String] and
      (JsPath \ "latitude").read[Double] and
      (JsPath \ "longitude").read[Double] and
      (JsPath \ "dateTime").read[LocalDateTime](dateTimeReads)
    ) (TaskDto.apply _)
}


