package controllers.rest.base

import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.Writes
import play.api.mvc.Result
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future

trait CRUDOperations[T, D] extends ApiActions {

  val dbConfigProvider: DatabaseConfigProvider
  val table: TableQuery[_ <: Table[T]]

  private def findOneById(id: Int)(handleQuery: Query[_ <: Table[T], T, Seq] => Future[Result]) = authorized.async { request =>
    handleQuery(findByIdAndUserId(id, request.token.get.userInfo.id))
  }

  def get(version: String, id: Int)(implicit writes: Writes[D]) = findOneById(id) { query =>
    val db = dbConfigProvider.get.db
    db.run(query.result.headOption)
      .map { optValue =>
        optValue.map(value => ok(toDto(value))).getOrElse(notFound)
      }
  }

  def delete(version: String, id: Int) = findOneById(id) { query =>
    val db = dbConfigProvider.get.db
    db.run(query.delete).map {
      case 1 => ok("Success")
      case _ => notFound
    }
  }

  def list(version: String, offset: Int, limit: Int)(implicit writes: Writes[D]) = authorized.async { request =>
    val db = dbConfigProvider.get.db
    val query = findByUser(request.token.get.userInfo.id)
    db.run(query.length.result zip query.take(limit).drop(offset)
      .sortBy(_.column[java.sql.Timestamp]("created_date").desc).result).map { result =>
      val dtos = result._2.map(value => toDto(value))
      ok(ListResponse(dtos, limit, offset, result._1))
    }
  }

  def findByIdAndUserId(id: Int, userId: Int): Query[_ <: Table[T], T, Seq] = {
    for {
      v <- table if v.column[Int]("id") === id && v.column[Int]("user_id") === userId
    } yield v
  }

  def findByUser(userId: Int): Query[_ <: Table[T], T, Seq] = {
    for {
      v <- table if v.column[Int]("user_id") === userId
    } yield v
  }

  def toDto(value: T): D
}
