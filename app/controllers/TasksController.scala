package controllers

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

import controllers.TasksController._
import controllers.base.BaseController
import models.Tables._
import play.api.Configuration
import play.api.data.validation.ValidationError
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{Reads, _}
import play.api.mvc.BodyParsers
import security.TokenStorage
import services.TookanService.AppointmentResponse
import services.{StripeService, TookanService}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global


class TasksController @Inject()(val tokenStorage: TokenStorage,
                                dbConfigProvider: DatabaseConfigProvider,
                                tookanService: TookanService,
                                stripeService: StripeService,
                                config: Configuration) extends BaseController {

  val db = dbConfigProvider.get.db

  def createTask = authorized.async(BodyParsers.parse.json) { request =>
    def onValidationSuccess(dto: TaskDto) = {

      def processPayment(tookanTask: AppointmentResponse) = {
        stripeService.charge(calculatePrice(dto.cleaningType, dto.hasInteriorCleaning), dto.token, tookanTask.jobId)
          .flatMap {
            case Left(error) =>
              tookanService.deleteTask(tookanTask.jobId)
                .map(response => badRequest(error.message, error.errorType))
            case Right(charge) =>
              val insertQuery = (Jobs.map(job => (job.jobId, job.jobToken, job.description, job.userId))
                returning Jobs.map(_.id)
                += ((tookanTask.jobId, tookanTask.jobToken, dto.description, request.token.get.userInfo.id)))
              db.run(insertQuery)
                .map(id => ok(tookanTask))
          }
      }

      tookanService.createAppointment(dto.pickupName, dto.pickupPhone, dto.pickupAddress,
        dto.description, dto.pickupDateTime, Some(dto.pickupLongitude), Some(dto.pickupLatitude), None)
        .flatMap {
          case Left(error) => wrapInFuture(badRequest(error))
          case Right(task) => processPayment(task)
        }
    }

    request.body.validate[TaskDto]
      .fold(jsonValidationFailedFuture, onValidationSuccess)
  }
}

object TasksController {

  def calculatePrice(index: Int, hasInteriorCleaning: Boolean) = {
    val price = index match {
      case 0 => 3000
      case 1 => 3500
      case 2 => 4000
      case _ => 0
    }
    if (hasInteriorCleaning) price + 500 else price
  }

  case class TaskDto(token: String,
                     description: String,
                     pickupName: String,
                     pickupEmail: Option[String],
                     pickupPhone: String,
                     pickupAddress: String,
                     pickupLatitude: Double,
                     pickupLongitude: Double,
                     pickupDateTime: LocalDateTime,
                     cleaningType: Int,
                     hasInteriorCleaning: Boolean)

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
      (JsPath \ "dateTime").read[LocalDateTime](dateTimeReads) and
      (JsPath \ "cleaningType").read[Int]
        .filter(ValidationError("Value must be in range from 0 to 2"))(v => v >= 0 && v <= 2) and
      (JsPath \ "hasInteriorCleaning").read[Boolean]
    ) (TaskDto.apply _)
}


