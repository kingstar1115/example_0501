package services.internal.cache

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

  override def setUserDeviceTokens(userId: Int, deviceTokens: List[String]): Boolean = {
    sedisPool.withClient { client =>
      Option(client.set(userId.toString, deviceTokens.mkString(" ").trim))
        .exists(_.equalsIgnoreCase("ok"))
    }
  }
}
