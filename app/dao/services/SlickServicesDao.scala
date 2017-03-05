package dao.services

import javax.inject.Inject

import dao.services.ServicesDao._
import dao.services.SlickServicesDao._
import models.Tables.{Services, _}
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SlickServicesDao @Inject()(dbConfigProvider: DatabaseConfigProvider) extends ServicesDao {

  private val db = dbConfigProvider.get.db

  def findById(id: Int): Query[Services, ServicesRow, Seq] = {
    Services.filter(_.id === id)
  }

  override def findByKey(key: String): Future[ServicesRow] = {
    db.run(Services.filter(_.key === key).result.head)
  }

  def findByIdWithExtras(id: Int, extras: Set[Int]): Future[ServiceWithExtras] = {
    val extrasQuery = ServicesExtras.filter(_.extraId inSet extras).join(Extras).on(_.extraId === _.id)
    val query = findById(id)
      .joinLeft(extrasQuery).on(_.id === _._1.serviceId)
      .map(result => (result._1, result._2.map(_._2)))
    db.run(query.result).map { resultSet =>
      val extras = resultSet.flatMap(_._2)
      ServiceWithExtras(resultSet.head._1, extras)
    }
  }

  def loadAllWithExtras: Future[(Seq[(ServicesRow, Option[ServicesExtrasRow])], Seq[ExtrasRow])] = {
    for {
      services <- db.run(Services.withExtras.result)
      extras <- db.run(Extras.result)
    } yield (services, extras)
  }
}

object SlickServicesDao {

  implicit class ServicesExtension[R[_]](query: Query[Services, ServicesRow, R]) {
    def withExtras: Query[(Services, Rep[Option[ServicesExtras]]), (ServicesRow, Option[ServicesExtrasRow]), R] =
      query.joinLeft(ServicesExtras).on(_.id === _.serviceId)
  }

}
