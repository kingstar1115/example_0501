package dao

import models.{BaseTable, Entity}
import play.api.db.slick.DatabaseConfigProvider
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future

trait EntityDao[T <: BaseTable[E], E <: Entity] extends QueryObject[T, E] {

  private val db = dbConfigProvider.get.db

  def dbConfigProvider: DatabaseConfigProvider

  def run[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = db.run(a)

  def findById(id: Int): Future[E] = run(findByIdQuery(id).result.head)

  def update(entity: E) = {
    val updateAction = findByIdQuery(entity.id).update(entity).transactionally
    run(updateAction)
  }

  def loadAll: Future[Seq[E]] = run(query.result)
}
