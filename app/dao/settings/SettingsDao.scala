package dao.settings

import com.google.inject.ImplementedBy
import models.Tables.SettingsRow

import scala.concurrent.Future

@ImplementedBy(classOf[SlickSettingsDao])
trait SettingsDao {

  def loadAll: Future[Seq[SettingsRow]]

  def findByKey(key: String): Future[Option[SettingsRow]]

}
