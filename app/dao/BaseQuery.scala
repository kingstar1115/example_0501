package dao

import models.{BaseTable, Entity}
import slick.dbio.Effect.Write
import slick.driver.PostgresDriver.api._
import slick.lifted.{CanBeQueryCondition, TableQuery}
import slick.profile.FixedSqlAction


trait BaseQuery[T <: BaseTable[E], E <: Entity] {

  def query: TableQuery[T]

  def findByIdQuery(id: Int): Query[T, E, Seq] = query.filter(_.id === id)

  def deleteByIdQuery(id: Int): FixedSqlAction[Int, NoStream, Write] = query.filter(_.id === id).delete

  def filter[C <: Rep[_]](expr: T => C)(implicit wt: CanBeQueryCondition[C]): Query[T, E, Seq] = {
    query.filter(expr)
  }
}
