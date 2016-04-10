package controllers.base

import play.api.Play.current
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{Json, JsPath, Reads}
import play.api.libs.ws.WSClient
import controllers.base.FacebookCalls._

trait FacebookCalls {

  val ws: WSClient

  implicit val dataReads = Json.reads[PictureData]
  implicit val pictureReads = Json.reads[Picture]
  implicit val facebookResponseDtoReads: Reads[FacebookResponseDto] = (
      (JsPath \ "id").read[String] and
      (JsPath \ "email").readNullable[String] and
      (JsPath \ "first_name").read[String] and
      (JsPath \ "last_name").read[String] and
      (JsPath \ "picture").read[Picture]
    )(FacebookResponseDto.apply _)

  def facebookMe(token: String) = {
    ws.url(facebookMeUrl)
      .withRequestTimeout(5000)
      .withQueryString("access_token" -> token, "fields" -> "id,first_name,last_name,email,picture.width(200).height(200)")
      .get()
  }
}

object FacebookCalls {

  val facebookUrl = current.configuration.getString("facebook.api").get
  val facebookMeUrl = s"$facebookUrl/me"

  case class FacebookResponseDto(id: String,
                                 email: Option[String],
                                 firstName: String,
                                 lastName: String,
                                 picture: Picture)

  case class Picture(data: PictureData)

  case class PictureData(url: String)

}
