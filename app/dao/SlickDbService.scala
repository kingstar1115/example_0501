package dao

import javax.inject.Inject

import models.{BaseTable, Entity}
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.JdbcProfile

import scala.concurrent.Future


class SlickDbService @Inject()(dbConfigProvider: DatabaseConfigProvider) extends SlickDriver {

  import profile.api._

  private val db = dbConfigProvider.get[JdbcProfile].db

  def findOne[T <: BaseTable[E], E <: Entity](query: Query[T, E, Seq]): Future[E] = db.run(query.result.head)

  def findOneOption[T <: BaseTable[E], E <: Entity](query: Query[T, E, Seq]): Future[Option[E]] = db.run(query.result.headOption)

  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = db.run(a)

}
