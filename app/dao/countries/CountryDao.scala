package dao.countries

import com.google.inject.ImplementedBy
import models.Country
import slick.dbio.StreamingDBIO

@ImplementedBy(classOf[SlickCountryDao])
trait CountryDao {
  def getAllCountries: StreamingDBIO[Seq[Country], Country]
}
