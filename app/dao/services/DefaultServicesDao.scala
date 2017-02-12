package dao.services

import models.Tables.{Services, _}
import slick.driver.PostgresDriver.api._


class DefaultServicesDao extends ServicesDao {

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
}
