package security

import play.api.libs.json.Json


case class AuthToken(key: String,
                     userInfo: UserInfo)

case class UserInfo(id: Int,
                    email: Option[String],
                    name: String,
                    surname: String,
                    verified: Boolean,
                    userType: Int)

case class AuthResponse(key: String,
                        name: String,
                        surname: String,
                        userType: Int,
                        verified: Boolean)

object AuthToken {
  implicit val userInfoFormat = Json.format[UserInfo]
  implicit val tokenFormat = Json.format[AuthToken]
  implicit val authResponseFormat = Json.format[AuthResponse]
}
