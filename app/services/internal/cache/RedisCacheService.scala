package services.internal.cache

import javax.inject.{Inject, Singleton}

import org.sedis.Pool
import play.api.libs.json.{Json, Reads, Writes}
import security.AuthToken

import scala.util.{Failure, Success, Try}

@Singleton
class RedisCacheService @Inject()(sedisPool: Pool) extends CacheService {
  override def getUserDeviceTokens(userId: Int): List[String] = {
    sedisPool.withClient { client =>
      client.get(userId.toString)
        .map(_.split(" ").toList)
        .getOrElse(List.empty)
    }
  }

  override def setUserDeviceTokens(userId: Int, deviceTokens: List[String]): Boolean = {
    sedisPool.withClient { client =>
      Option(client.set(userId.toString, deviceTokens.mkString(" ").trim))
        .exists(_.equalsIgnoreCase("ok"))
    }
  }

  override def saveValue[T](key: String, data: T)(implicit writes: Writes[T]): Boolean = {
    sedisPool.withClient { client =>
      Option(client.set(key, Json.toJson(data).toString()))
        .exists(_.equalsIgnoreCase("ok"))
    }
  }

  override def getValue[T](key: String)(implicit reads: Reads[T]): Option[T] = {
    sedisPool.withClient { client =>
      Try {
        client.get(key).map(value => Some(Json.parse(value).as[T])).getOrElse(None)
      } match {
        case Success(value) => value
        case Failure(e) => None
      }
    }
  }
}
