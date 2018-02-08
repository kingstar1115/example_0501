package services.external.vehicles

import javax.inject.Inject

import commons.monads.transformers.OptionT
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.{WSClient, WSResponse}
import services.external.vehicles.FuelEconomyVehicleDataService._
import services.external.vehicles.VehicleDataService.{Item, VehicleModel}

import scala.concurrent.Future
import scala.xml.NodeSeq

class FuelEconomyVehicleDataService @Inject()(ws: WSClient) extends VehicleDataService {

  override def getAvailableYears(): Future[Seq[Item]] = {
    ws.url(BaseURL.concat(Years)).get()
      .map(response => convertResponse(response))
  }

  override def getMakesByYear(year: Int): Future[Seq[Item]] = {
    ws.url(BaseURL.concat(Makes))
      .withQueryString(("year", year.toString)).get()
      .map(response => convertResponse(response))
  }

  override def getModelsByYearAndMake(year: Int, make: String): Future[Seq[Item]] = {
    ws.url(BaseURL.concat(Models))
      .withQueryString(
        ("year", year.toString),
        ("make", make)
      ).get()
      .map(response => convertResponse(response))
  }

  private def convertResponse(response: WSResponse): Seq[Item] = {
    val items = response.xml \\ "menuItem"
    items.map(convertMenuItem)
  }

  private def convertMenuItem(menuItem: NodeSeq) = {
    Item((menuItem \ "text").text, (menuItem \ "value").text)
  }

  override def isLargeVehicle(vehicleModel: VehicleModel): Future[Boolean] = {
    (for {
      id <- OptionT(getModelId(vehicleModel))
      vehicleSize <- OptionT(getVehicleSize(id))
    } yield vehicleSize).inner
      .map(_.fold(false)(vehicleSize => !SmallCarSizes.contains(vehicleSize)))
  }

  private def getModelId(vehicleModel: VehicleModel): Future[Option[String]] = {
    ws.url(BaseURL.concat(Options))
      .withQueryString(
        ("year", vehicleModel.year.toString),
        ("make", vehicleModel.make),
        ("model", vehicleModel.model)
      ).get()
      .map(response => {
        val options = response.xml \\ "menuItem"
        options.headOption
          .map(option => (option \ "value").text)
      })
  }

  private def getVehicleSize(id: String): Future[Option[String]] = {
    ws.url(BaseURL.concat(s"/$id")).get()
      .map(response => {
        Some((response.xml \\ "VClass").text)
      })
  }
}

object FuelEconomyVehicleDataService {
  val BaseURL = "https://www.fueleconomy.gov/ws/rest/vehicle"
  val Years = "/menu/year"
  val Makes = "/menu/make"
  val Models = "/menu/model"
  val Options = "/menu/options"

  val SmallCarSizes = Seq(
    "Two-Seaters",
    "Minicompact Cars",
    "Subcompact Cars",
    "Compact Cars",
    "Mid-Size Cars",
    "Large Cars",
    "Small Station Wagons",
    "Mid-Size Cars Station Wagons"
  )
}
