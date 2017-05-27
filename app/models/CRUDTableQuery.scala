package models

import dao.SlickDriver
import slick.dbio.Effect.Write
import slick.lifted.TableQuery
import slick.profile.FixedSqlAction


trait CRUDTableQuery[T <: BaseTable[E], E <: Entity] extends TableQuery[T] with SlickDriver {

  import profile.api._

  def findById(id: Int): Query[T, E, Seq] = filter(_.id === id)

  def delete(id: Int): FixedSqlAction[Int, NoStream, Write] = findById(id).delete

  def update(entity: E): FixedSqlAction[Int, NoStream, Write] = findById(entity.id).update(entity)

  def insert(entity: E): FixedSqlAction[Int, NoStream, Write] = this += entity

  def insertAll(entities: Seq[E]): FixedSqlAction[Option[Int], NoStream, Write] = this ++= entities
}
