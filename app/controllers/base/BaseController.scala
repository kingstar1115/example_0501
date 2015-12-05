package controllers.base

import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc.Controller
import security.TokenStorage
import slick.driver.JdbcProfile

abstract class BaseController(tokenStorage: TokenStorage,
                              dbConfigProvider: DatabaseConfigProvider) extends Controller with ApiActions {

  override val ts: TokenStorage = tokenStorage

  val db = dbConfigProvider.get[JdbcProfile].db
}
