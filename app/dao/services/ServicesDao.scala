package dao.services

import com.google.inject.ImplementedBy
import models.Tables._
import slick.lifted.Query

import scala.concurrent.Future

@ImplementedBy(classOf[DefaultServicesDao])
trait ServicesDao {

  def findById(id: Int): Query[Services, ServicesRow, Seq]

  def findByKey(key: String): Future[ServicesRow]

  def findByIdWithExtras(id: Int, extras: Set[Int]): Future[Seq[(ServicesRow, Option[ExtrasRow])]]

  def loadAllWithExtras: Future[(Seq[(ServicesRow, Option[ServicesExtrasRow])], Seq[ExtrasRow])]
}
