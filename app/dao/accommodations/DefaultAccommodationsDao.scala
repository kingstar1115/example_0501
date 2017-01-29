package dao.accommodations

import models.Tables._
import slick.driver.PostgresDriver.api._


class DefaultAccommodationsDao extends AccommodationsDao {

  val exteriorCleaning = "EXTERIOR_CLEANING"
  val exteriorAndInteriorCleaning = "EXTERIOR_AND_INTERIOR_CLEANING"

  def findById(id: Int): Query[Accommodations, AccommodationsRow, Seq] = {
    Accommodations.filter(_.id === id)
  }

  def findByIdWithExtras(id: Int, extras: Set[Int]): Query[(Accommodations, Rep[Option[Extras]]), (AccommodationsRow, Option[ExtrasRow]), Seq] = {
    val extrasQuery = AccommodationsExtras.filter(_.extraId inSet extras).join(Extras).on(_.extraId === _.id)
    findById(id)
      .joinLeft(extrasQuery).on(_.id === _._1.serviceId)
      .map(result => (result._1, result._2.map(_._2)))
  }

  def getExteriorCleaning: Query[Accommodations, AccommodationsRow, Seq] = {
    getByKey(exteriorCleaning)
  }

  def getExteriorAndInteriorCleaning: Query[Accommodations, AccommodationsRow, Seq] = {
    getByKey(exteriorAndInteriorCleaning)
  }

  private def getByKey(key: String) = {
    Accommodations.filter(accommodation => accommodation.key.isDefined && accommodation.key === Option(key))
  }
}
