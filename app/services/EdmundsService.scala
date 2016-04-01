package services

import javax.inject.Inject

import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.libs.ws.{WSClient, WSRequest}
import play.api.{Configuration, Logger}
import services.EdmundsService._

import scala.concurrent.ExecutionContext.Implicits.global

class EdmundsService @Inject()(ws: WSClient,
                               configuration: Configuration) {

  val API_KEY = configuration.getString("edmunds.key").get

  def getCarMakers(parameters: (String, String)*) = {
    ws.buildUrl(MAKERS)
      .withParameters(parameters: _*)
      .get
      .map { wsResponse =>
        wsResponse.status match {
          case 200 =>
            wsResponse.json.validate[Makers](makersFormat) match {
              case JsSuccess(data, p) => Some(data)
              case JsError(errors) =>
                Logger.debug(s"Can't fetch Edmunds data. Errors: $errors")
                None
            }
          case _ => None
        }
      }
  }

  implicit class WSClientExt(ws: WSClient) {

    def buildUrl(path: String) = {
      ws.url(BASE_URL.concat(path))
    }

  }

  implicit class WSRequestExt(request: WSRequest) {

    def withParameters(parameters: (String, String)*) = {
      val fullParameters = List("api_key" -> API_KEY,
        "fmt" -> "json") ++ parameters
      request.withQueryString(fullParameters: _*)
    }

  }

}

object EdmundsService {

  val BASE_URL = "https://api.edmunds.com/api/vehicle/v2/"
  val MAKERS = "makes"

  case class Makers(makes: List[Maker],
                    makesCount: Int)

  case class Maker(id: Int,
                   name: String,
                   niceName: String,
                   models: List[Model])

  case class Model(id: String,
                   name: String,
                   niceName: String,
                   years: List[Year])

  case class Year(id: Int,
                  year: Int)

  implicit val yearFormat = Json.format[Year]
  implicit val modelFormat = Json.format[Model]
  implicit val makerFormat = Json.format[Maker]
  implicit val makersFormat = Json.format[Makers]
}
