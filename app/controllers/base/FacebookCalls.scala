package controllers.base

import play.api.Play.current
import play.api.libs.functional.syntax._
import play.api.libs.json.Reads._
import play.api.libs.json.{JsPath, Reads}
import play.api.libs.ws.WSClient
import controllers.base.FacebookCalls._


trait FacebookCalls {

  val ws: WSClient

  implicit val facebookResponseDtoReads: Reads[FacebookResponseDto] = (
      (JsPath \ "id").read[String] and
      (JsPath \ "email").readNullable[String](email)
    )(FacebookResponseDto.apply _)

  case class FacebookResponseDto(id: String,
                                 email: Option[String])

  def facebookMe(token: String) = {
    ws.url(facebookMeUrl)
      .withRequestTimeout(5000)
      .withQueryString("access_token" -> token, "fields" -> "email")
      .get()
  }
}

object FacebookCalls {

  val facebookUrl = current.configuration.getString("facebook.api").get
  val facebookMeUrl = s"$facebookUrl/me"

}
