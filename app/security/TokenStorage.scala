package security

import javax.inject.{Inject, Singleton}

import org.sedis.Pool
import play.api.libs.json.Json

import scala.util.{Success, Failure, Try}

@Singleton
class TokenStorage @Inject()(sedisPool: Pool) {

  def setToken(token: AuthToken) = {
    sedisPool.withClient { client =>
      client.set(token.key, Json.toJson(token).toString())
      token
    }
  }

  def getToken(key: String): Option[AuthToken] = {
    sedisPool.withClient { client =>
      Try {
        client.get(key).map(value => Some(Json.parse(value).as[AuthToken])).getOrElse(None)
      } match {
        case Success(token) => token
        case Failure(e) => None
      }
    }
  }

  def deleteToken(token: AuthToken) = {
    sedisPool.withClient { client =>
      client.del(token.key)
    }
  }

}
