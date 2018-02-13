package controllers.rest.base

import javax.inject.Inject

import security.TokenStorage
import services.external.vehicles.VehicleDataService

class VehicleTypesController @Inject()(val tokenStorage: TokenStorage,
                                       vehicleDataService: VehicleDataService) extends BaseController {


//  def getBodyTypes: Action[AnyContent] = Action.async { _ =>
//    (for {
//      years <- vehicleDataService.getAvailableYears()
//      yearsWithMakes <- getMakesByYears(years)
//      models <- getModels(yearsWithMakes)
//      types <- getTypes(models)
//    } yield types).map(types => ok(types))/**/
//  }
//
//  private def getMakesByYears(years: Seq[Item]): Future[Seq[YearWithMake]] = {
//    val makesByYears = years.map(_.value.toInt)
//      .filter(year => year >= 2005 && year <= 2009)
//      .map(year => {
//        vehicleDataService.getMakesByYear(year)
//          .map(makes => makes.map(make => YearWithMake(year, make.value)))
//      })
//    Future.sequence(makesByYears).map(col => col.flatten)
//  }
//
//  private def getModels(yearWithMakes: Seq[YearWithMake]): Future[Seq[YearWithMakeAndModel]] = {
//    val makesWithModels: Seq[Future[Seq[YearWithMakeAndModel]]] = yearWithMakes
//      .map(make => {
//        vehicleDataService.getModelsByYearAndMake(make.year, make.make)
//          .map(models => models.map(model => YearWithMakeAndModel(make.year, make.make, model.value)))
//      })
//    Future.sequence(makesWithModels).map(col => col.flatten)
//  }
//
//  private def getTypes(models: Seq[YearWithMakeAndModel]): Future[Set[String]] = {
//    val types = models.map(model => {
//      val time = new Random().nextInt(2000)
//      Thread.sleep(time.toLong)
//      vehicleDataService.getBodyType(VehicleModel(model.year, model.make, model.model))
//        .map(bodyType => {
//          println(bodyType)
//          bodyType
//        })
//    })
//    Future.sequence(types).map(_.flatten.toSet)
//  }
}

object VehicleTypesController {

  case class YearWithMake(year: Int, make: String)

  case class YearWithMakeAndModel(year: Int, make: String, model: String)

}
