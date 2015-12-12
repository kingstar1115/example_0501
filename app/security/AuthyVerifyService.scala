package security

import javax.inject.Inject

import com.google.inject.Singleton
import play.api.Configuration
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.functional.syntax._
import play.api.libs.json.{Writes, _}
import play.api.libs.ws.WSClient
import security.AuthyVerifyService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


@Singleton
class AuthyVerifyService @Inject()(ws: WSClient,
                                    config: Configuration) {

  val apiKey = config.getString("authy.key").get
  val baseVerifyUrl = "https://api.authy.com/protected/json/phones/verification"

  /**
   * @param phone phone number to verify
   * @param countryCode code of the country. Default - 1(USA)
   */
  def sendVerifyCode(countryCode: Int = 1, phone: String) = {
    val requestDto = VerifyDto("sms", countryCode, phone)
    Try {
      req("start").withHeaders(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON)
        .withQueryString("api_key" -> apiKey)
        .post(Json.toJson(requestDto))
    } match {
      case Success(wsResponse) => wsResponse.map { resp => resp.json.as[AuthyResponseDto] }
      case Failure(e) => Future.failed(e)
    }
  }

  def checkVerifyCode(countryCode: String, phone: String, verifyCode: String) = {
    Try {
      req("check")
        .withQueryString("api_key" -> apiKey, "country_code" -> countryCode,
          "phone_number" -> phone, "verification_code" -> verifyCode)
        .get()
    } match {
      case Success(wsResponse) => wsResponse.map(_.json.as[AuthyResponseDto])
      case Failure(e) =>
        val errorDto = AuthyResponseDto(success = false, s"Failed to send verify request. Cause: ${e.getMessage}}")
        Future.successful(errorDto)
    }
  }

  def req(path: String) = {
    ws.url(s"$baseVerifyUrl/$path")
  }
}

object AuthyVerifyService {

  case class VerifyDto(via: String,
                       countryCode: Int,
                       phoneNumber: String)

  case class AuthyResponseDto(success: Boolean,
                              message: String)

  implicit val verifyDtoWrites: Writes[VerifyDto] = (
      (__ \ "via").write[String] and
      (__ \ "country_code").write[Int] and
      (__ \ "phone_number").write[String]
    )(unlift(VerifyDto.unapply))

  implicit val authyResponseDtoReads: Reads[AuthyResponseDto] = (
      (__ \ "success").read[Boolean] and
      (__ \ "message").read[String]
    )(AuthyResponseDto.apply _)
}
