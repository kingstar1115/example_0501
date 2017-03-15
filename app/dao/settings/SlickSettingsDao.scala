package dao.settings

import javax.inject.Inject

import models.Tables._
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.PostgresDriver.api._
import slick.lifted.TableQuery

import scala.concurrent.Future


class SlickSettingsDao @Inject()(val dbConfigProvider: DatabaseConfigProvider) extends SettingsDao {

  override def query: TableQuery[Settings] = Settings

  override def findByKey(key: String): Future[Option[SettingsRow]] = {
    run(filter(_.key === key).result.headOption)
  }
}
