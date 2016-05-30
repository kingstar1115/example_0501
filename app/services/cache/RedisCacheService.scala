package services.cache

import javax.inject.{Inject, Singleton}

import org.sedis.Pool

@Singleton
class RedisCacheService @Inject()(sedisPool: Pool) extends CacheService {
  override def getUserDeviceTokens(userId: Int): List[String] = {
    sedisPool.withClient { client =>
      client.get(userId.toString)
        .map(_.split(" ").toList)
        .getOrElse(List.empty)
    }
  }

  override def setUserDeviceTokens(userId: Int, deviceTokens: List[String]): Unit = {
    sedisPool.withClient { client =>
      client.set(userId.toString, deviceTokens.mkString(" "))
    }
  }
}
