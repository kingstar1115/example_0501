package dao.accommodations

import com.google.inject.ImplementedBy
import models.Tables.{Accommodations, AccommodationsRow, Extras, ExtrasRow}
import slick.lifted.{Query, Rep}

@ImplementedBy(classOf[DefaultAccommodationsDao])
trait AccommodationsDao {

  def findById(id: Int): Query[Accommodations, AccommodationsRow, Seq]

  def findByIdWithExtras(id: Int, extras: Set[Int]): Query[(Accommodations, Rep[Option[Extras]]), (AccommodationsRow, Option[ExtrasRow]), Seq]

  def getExteriorCleaning: Query[Accommodations, AccommodationsRow, Seq]

  def getExteriorAndInteriorCleaning: Query[Accommodations, AccommodationsRow, Seq]
}
