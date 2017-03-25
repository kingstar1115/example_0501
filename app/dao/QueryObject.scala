package dao

import models.{BaseTable, Entity}
import slick.dbio.Effect.Write
import slick.lifted.CanBeQueryCondition
import slick.profile.FixedSqlAction


trait QueryObject[T <: BaseTable[E], E <: Entity] extends SlickDriver {

  import profile.api._

  def query: TableQuery[T]

  def findByIdQuery(id: Int): Query[T, E, Seq] = query.filter(_.id === id)

  def deleteByIdQuery(id: Int): FixedSqlAction[Int, NoStream, Write] = query.filter(_.id === id).delete

  def updateQuery(entity: E) = findByIdQuery(entity.id).update(entity)

  def filter[C <: Rep[_]](expr: T => C)(implicit wt: CanBeQueryCondition[C]): Query[T, E, Seq] = {
    query.filter(expr)
  }
}
