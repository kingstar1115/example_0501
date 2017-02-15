package dao.services

import javax.inject.Inject

import dao.services.DefaultServicesDao._
import models.Tables.{Services, _}
import play.api.db.slick.DatabaseConfigProvider
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class DefaultServicesDao @Inject()(dbConfigProvider: DatabaseConfigProvider) extends ServicesDao {

  private val db = dbConfigProvider.get.db

  val exteriorCleaning = "EXTERIOR_CLEANING"
  val exteriorAndInteriorCleaning = "EXTERIOR_AND_INTERIOR_CLEANING"

  def findById(id: Int): Query[Services, ServicesRow, Seq] = {
    Services.filter(_.id === id)
  }

  def findByIdWithExtras(id: Int, extras: Set[Int]): Query[(Services, Rep[Option[Extras]]), (ServicesRow, Option[ExtrasRow]), Seq] = {
    val extrasQuery = ServicesExtras.filter(_.extraId inSet extras).join(Extras).on(_.extraId === _.id)
    findById(id)
      .joinLeft(extrasQuery).on(_.id === _._1.serviceId)
      .map(result => (result._1, result._2.map(_._2)))
  }

  def getExteriorCleaning: Query[Services, ServicesRow, Seq] = {
    getByKey(exteriorCleaning)
  }

  def getExteriorAndInteriorCleaning: Query[Services, ServicesRow, Seq] = {
    getByKey(exteriorAndInteriorCleaning)
  }

  private def getByKey(key: String) = {
    Services.filter(_.key === key)
  }

  def loadAllWithExtras: Future[(Seq[(ServicesRow, Option[ServicesExtrasRow])], Seq[ExtrasRow])] = {
    for {
      services <- db.run(Services.withExtras.result)
      extras <- db.run(Extras.result)
    } yield (services, extras)
  }
}

object DefaultServicesDao {

  implicit class ServicesExtension[R[_]](query: Query[Services, ServicesRow, R]) {
    def withExtras: Query[(Services, Rep[Option[ServicesExtras]]), (ServicesRow, Option[ServicesExtrasRow]), R] =
      query.joinLeft(ServicesExtras).on(_.id === _.serviceId)
  }

}
