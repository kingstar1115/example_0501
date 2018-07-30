package controllers.rest.base

import commons.ServerError
import commons.enums.FacebookError
import controllers.rest.base.FacebookCalls._
import play.api.Play.current
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.api.libs.ws.WSClient

import play.api.libs.concurrent.Execution.Implicits._

import scala.concurrent.Future

trait FacebookCalls {

  def ws: WSClient

  def facebookMe(token: String): Future[Either[ServerError, FacebookProfile]] = {
    ws.url(FacebookMeUrl)
      .withRequestTimeout(5000)
      .withQueryString("access_token" -> token, "fields" -> "id,first_name,last_name,email,picture.width(200).height(200)")
      .get()
      .map(wsResponse => {
        wsResponse.status match {
          case 200 =>
            wsResponse.json.validate[FacebookProfile] match {
              case JsSuccess(facebookProfile, _) => Right(facebookProfile)
              case JsError(e) => Left(ServerError(s"Failed to parse facebook response: $e"))
            }
          case failed =>
            Left(ServerError(s"Failed to load facebook profile. Error code: $failed", Option(FacebookError)))
        }
      })
  }
}

object FacebookCalls {

  val FacebookUrl: String = current.configuration.getString("facebook.api").get
  val FacebookMeUrl = s"$FacebookUrl/me"

  case class FacebookProfile(id: String,
                             email: Option[String],
                             firstName: String,
                             lastName: String,
                             picture: Picture)

  object FacebookProfile {
    implicit val jsonReads: Reads[FacebookProfile] = (
      (JsPath \ "id").read[String] and
        (JsPath \ "email").readNullable[String] and
        (JsPath \ "first_name").read[String] and
        (JsPath \ "last_name").read[String] and
        (JsPath \ "picture").read[Picture]
      ) (FacebookProfile.apply _)
  }

  case class Picture(data: PictureData)

  object Picture {
    implicit val jsonReads: Reads[Picture] = Json.reads[Picture]
  }

  case class PictureData(url: String)

  object PictureData {
    implicit val jsonReads: Reads[PictureData] = Json.reads[PictureData]
  }

}
