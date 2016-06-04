package services.internal.cache

import javax.inject.{Inject, Singleton}

import org.sedis.Pool
import play.api.libs.json.{Json, Reads, Writes}
import security.AuthToken

import scala.util.{Failure, Success, Try}

@Singleton
class RedisCacheService @Inject()(sedisPool: Pool) extends CacheService {

  override def set[T](key: String, data: T)(implicit writes: Writes[T]): Boolean = {
    sedisPool.withClient { client =>
      Option(client.set(key, Json.toJson(data).toString()))
        .exists(_.equalsIgnoreCase("ok"))
    }
  }

  override def get[T](key: String)(implicit reads: Reads[T]): Option[T] = {
    sedisPool.withClient { client =>
      Try {
        client.get(key).map(value => Some(Json.parse(value).as[T])).getOrElse(None)
      } match {
        case Success(value) => value
        case Failure(e) => None
      }
    }
  }

  override def delete(key: String): Long = {
    sedisPool.withClient { client =>
      client.del(key)
    }
  }
}
