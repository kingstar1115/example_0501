package services.internal.cache

import com.google.inject.ImplementedBy

@ImplementedBy(classOf[RedisCacheService])
trait CacheService {

  def getUserDeviceTokens(userId: Int): List[String]

  def setUserDeviceTokens(userId: Int, deviceTokens: List[String]): Boolean
}
