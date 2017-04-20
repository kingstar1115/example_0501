package controllers.admin

import play.api.mvc.{Action, Controller}
import views.html.admin.main

class DashboardController extends Controller {

  def getAdminDashboard = Action { _ =>
    Ok(main(null)(null))
  }

}
