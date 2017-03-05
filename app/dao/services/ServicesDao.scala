package dao.services

import com.google.inject.ImplementedBy
import dao.services.ServicesDao._
import slick.lifted.Query

import scala.concurrent.Future

@ImplementedBy(classOf[SlickServicesDao])
trait ServicesDao {

  def findById(id: Int): Query[Services, ServicesRow, Seq]

  def findByKey(key: String): Future[ServicesRow]

  def findByIdWithExtras(id: Int, extras: Set[Int]): Future[ServiceWithExtras]

  def loadAllWithExtras: Future[(Seq[(ServicesRow, Option[ServicesExtrasRow])], Seq[ExtrasRow])]
}

object ServicesDao {

  case class ServiceWithExtras(service: ServicesRow, extras: Seq[ExtrasRow])

}
