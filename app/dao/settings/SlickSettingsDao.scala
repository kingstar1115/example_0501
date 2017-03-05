package dao.settings

import javax.inject.Inject

import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future


class SlickSettingsDao @Inject()(dbConfigProvider: DatabaseConfigProvider) extends SettingsDao {

  private val db = dbConfigProvider.get.db

  override def loadAll: Future[Seq[SettingsRow]] = {
    db.run(Settings.result)
  }

  override def findByKey(key: String): Future[Option[SettingsRow]] = {
    db.run(Settings.filter(_.key === key).result.headOption)
  }
}
