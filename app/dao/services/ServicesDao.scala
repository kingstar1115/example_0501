package dao.services

import com.google.inject.ImplementedBy
import models.Tables._
import slick.lifted.{Query, Rep}

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultServicesDao])
trait ServicesDao {

  def findById(id: Int): Query[Services, ServicesRow, Seq]

  def findByIdWithExtras(id: Int, extras: Set[Int]): Query[(Services, Rep[Option[Extras]]), (ServicesRow, Option[ExtrasRow]), Seq]

  def getExteriorCleaning: Query[Services, ServicesRow, Seq]

  def getExteriorAndInteriorCleaning: Query[Services, ServicesRow, Seq]

  def loadAllWithExtras: Future[(Seq[(ServicesRow, Option[ServicesExtrasRow])], Seq[ExtrasRow])]
}
