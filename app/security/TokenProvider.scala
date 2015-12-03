package security

import javax.inject.{Inject, Singleton}

import play.api.libs.Crypto

@Singleton
class TokenProvider @Inject()(crypto: Crypto) {

  def generateToken(userInfo: UserInfo) = {
    AuthToken(crypto.generateToken, userInfo)
  }
}
