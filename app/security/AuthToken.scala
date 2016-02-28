package security

import play.api.libs.json.Json


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
                        phone: String)

object AuthToken {
  implicit val userInfoFormat = Json.format[UserInfo]
  implicit val tokenFormat = Json.format[AuthToken]
  implicit val authResponseFormat = Json.format[AuthResponse]
}
