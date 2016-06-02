package services.internal.cache

import com.google.inject.ImplementedBy
import play.api.libs.json.{Reads, Writes}

@ImplementedBy(classOf[RedisCacheService])
trait CacheService {

  def getUserDeviceTokens(userId: Int): List[String]

  def setUserDeviceTokens(userId: Int, deviceTokens: List[String]): Boolean

  def saveValue[T](key: String, data: T)(implicit writes: Writes[T]): Boolean

  def getValue[T](key: String)(implicit reads: Reads[T]): Option[T]
}
