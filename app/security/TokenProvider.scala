package security

import javax.inject.{Inject, Singleton}

import play.api.libs.Crypto

@Singleton
class TokenProvider @Inject()(crypto: Crypto) {

  def generateToken(userInfo: UserInfo): AuthToken = {
    AuthToken(generateKey, userInfo)
  }

  def generateKey: String = crypto.generateToken
}
