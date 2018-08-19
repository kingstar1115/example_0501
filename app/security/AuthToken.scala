package security

import play.api.libs.json.{Format, Json}


case class AuthToken(key: String,
                     userInfo: UserInfo)

case class UserInfo(id: Int,
                    email: String,
                    firstName: String,
                    lastName: String,
                    verified: Boolean,
                    userType: Int,
                    picture: Option[String])

case class AuthResponse(key: String,
                        firstName: String,
                        lastName: String,
                        userType: Int,
                        verified: Boolean,
                        picture: Option[String],
                        phone: String,
                        email: String)

object AuthToken {
  implicit val userInfoFormat: Format[UserInfo] = Json.format[UserInfo]
  implicit val tokenFormat: Format[AuthToken] = Json.format[AuthToken]
  implicit val authResponseFormat: Format[AuthResponse] = Json.format[AuthResponse]
}
