package config.filters

import javax.inject.Inject

import play.api.http.HttpFilters
import play.filters.cors.CORSFilter

class Filters @Inject()(corsFilter: CORSFilter,
                        loggingFilter: LoggingFilter) extends HttpFilters {

  override def filters = Seq(corsFilter, loggingFilter)

}
