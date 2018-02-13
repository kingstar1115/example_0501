package services.external.vehicles

import com.google.inject.ImplementedBy
import play.api.libs.json.{Format, Json}
import services.external.vehicles.VehicleDataService._

import scala.concurrent.Future


@ImplementedBy(classOf[FuelEconomyVehicleDataService])
trait VehicleDataService {

  def getAvailableYears(): Future[Seq[Item]]

  def getMakesByYear(year: Int): Future[Seq[Item]]

  def getModelsByYearAndMake(year: Int, make: String): Future[Seq[Item]]

  def isLargeVehicle(vehicleModel: VehicleModel): Future[Boolean]
}

object VehicleDataService {

  case class Item(name: String, value: String)

  object Item {
    implicit val jsonFormat: Format[Item] = Json.format[Item]
  }

  case class VehicleModel(year: Int, make: String, model: String)
}
