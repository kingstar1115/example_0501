package dao.settings

import com.google.inject.ImplementedBy
import dao.EntityDao
import models.Tables._

import scala.concurrent.Future

@ImplementedBy(classOf[SlickSettingsDao])
trait SettingsDao extends EntityDao[Settings, SettingsRow] {

  def findByKey(key: String): Future[Option[SettingsRow]]

}
