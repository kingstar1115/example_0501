package models

import slick.driver.PostgresDriver.api._
import slick.lifted.{Rep, Tag}

import scala.reflect.ClassTag

abstract class BaseTable[E: ClassTag](tag: Tag, schemaName: Option[String], tableName: String)
  extends Table[E](tag, schemaName, tableName) {

  def this(_tableTag: Tag, _tableName: String) = this(_tableTag, None, _tableName)

  def id: Rep[Int]

  def createdDate: Rep[java.sql.Timestamp]
}

