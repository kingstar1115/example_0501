package services.internal.cache

import com.google.inject.ImplementedBy
import play.api.libs.json.{Reads, Writes}

@ImplementedBy(classOf[RedisCacheService])
trait CacheService {

  def set[T](key: String, data: T)(implicit writes: Writes[T]): Boolean

  def get[T](key: String)(implicit reads: Reads[T]): Option[T]

  def delete(key: String): Long
}
