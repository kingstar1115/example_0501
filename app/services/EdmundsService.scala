package services

import javax.inject.Inject

import play.api.libs.json.{Format, JsError, JsSuccess, Json}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.{Configuration, Logger}
import services.EdmundsService._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EdmundsService @Inject()(ws: WSClient,
                               configuration: Configuration) {

  private val apiKey = configuration.getString("edmunds.key").get

  def getCarMakers(parameters: (String, String)*): Future[Option[Makers]] = {
    ws.buildUrl(makes)
      .withParameters(parameters: _*)
      .get
      .map { wsResponse =>
        wsResponse.status match {
          case 200 =>
            wsResponse.json.validate[Makers](makersFormat) match {
              case JsSuccess(data, _) => Some(data)
              case JsError(errors) =>
                Logger.debug(s"Can't fetch Edmunds data. Errors: $errors")
                None
            }
          case _ =>
            None
        }
      }
  }

  def getCarStyle(make: String, model: String, year: Int): Future[Option[Style]] = {
    val styles = s"$make/$model/$year/styles"
    ws.buildUrl(styles)
      .withParameters(("view", "full"))
      .get
      .map { wsResponse =>
        wsResponse.status match {
          case 200 =>
            wsResponse.json.validate[Styles](stylesFormat) match {
              case JsSuccess(styles, _) =>
                styles.styles.headOption
              case JsError(errors) =>
                Logger.debug(s"Can't fetch  vehicle($make/$model/) styles. Errors: $errors")
                None
            }
          case _ =>
            None
        }
      }
  }

  implicit class WSClientExt(ws: WSClient) {

    def buildUrl(path: String) = {
      ws.url(vehicleApiUrl.concat(path))
    }

  }

  implicit class WSRequestExt(request: WSRequest) {

    def withParameters(parameters: (String, String)*) = {
      val fullParameters = List("api_key" -> apiKey,
        "fmt" -> "json") ++ parameters
      request.withQueryString(fullParameters: _*)
    }

  }

}

object EdmundsService {

  val vehicleApiUrl = "https://api.edmunds.com/api/vehicle/v2/"
  val makes = "makes"

  case class Makers(makes: List[Maker], makesCount: Int)

  case class Maker(id: Int, name: String, niceName: String, models: List[Model])

  case class Model(id: String, name: String, niceName: String, years: List[Year])

  case class Year(id: Int,
                  year: Int)

  implicit val yearFormat: Format[Year] = Json.format[Year]
  implicit val modelFormat: Format[Model] = Json.format[Model]
  implicit val makerFormat: Format[Maker] = Json.format[Maker]
  implicit val makersFormat: Format[Makers] = Json.format[Makers]

  case class Categories(vehicleType: String)

  case class Style(id: Long, categories: Categories)

  case class Styles(styles: Seq[Style])

  implicit val categoriesFormat: Format[Categories] = Json.format[Categories]
  implicit val styleFormat: Format[Style] = Json.format[Style]
  implicit val stylesFormat: Format[Styles] = Json.format[Styles]

}
