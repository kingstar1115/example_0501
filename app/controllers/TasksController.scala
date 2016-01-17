package controllers

import javax.inject.Inject

import controllers.TasksController._
import controllers.base.BaseController
import play.api.Logger
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{Reads, _}
import play.api.mvc.{Action, BodyParsers}
import security.TokenStorage
import services.ApnsPushService


class TasksController @Inject()(val tokenStorage: TokenStorage,
                                apnsPushService: ApnsPushService) extends BaseController {

  def onStatusChange = Action(BodyParsers.parse.json) { request =>
    request.body.validate[WebHookDto] match {
      case JsSuccess(dto, p) =>

      case _ => Logger.debug("Incorrect data from tookan")
    }
    Ok
  }
}

object TasksController {

  case class WebHookDto(userId: Int,
                        jobId: Int,
                        jobStatus: Int)

  implicit val webHookDtoReads: Reads[WebHookDto] = (
    (JsPath \ "user_id").read[Int] and
    (JsPath \ "job_id").read[Int] and
    (JsPath \ "jobStatus").read[Int]
    )(WebHookDto.apply _)

}


