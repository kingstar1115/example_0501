package dao.settings

import javax.inject.Inject

import dao.SlickDriver
import models.Tables._
import play.api.db.slick.DatabaseConfigProvider

import scala.concurrent.Future


class SlickSettingsDao @Inject()(val dbConfigProvider: DatabaseConfigProvider) extends SettingsDao with SlickDriver {

  import profile.api._

  override def query: TableQuery[Settings] = Settings

  override def findByKey(key: String): Future[Option[SettingsRow]] = {
    run(filter(_.key === key).result.headOption)
  }
}
