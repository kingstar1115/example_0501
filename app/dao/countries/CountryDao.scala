package dao.countries

import com.google.inject.ImplementedBy
import dao.countries.CountryDao._
import slick.dbio.Effect.Read
import slick.dbio.{DBIOAction, NoStream, Streaming}

@ImplementedBy(classOf[SlickCountryDao])
trait CountryDao {
  def getAllCountries: DBIOAction[Seq[Country], Streaming[Country], Read]

  def getCountriesWithZipCodes(ids: Set[Int]): DBIOAction[Seq[CountryWithZipCodes], NoStream, Read]

  def getDefaultCountry: DBIOAction[Country, NoStream, Read]
}

object CountryDao {

  case class Country(id: Int, createdDate: java.sql.Timestamp, name: String, code: String, default: Boolean)

  case class ZipCode(id: Int, createdDate: java.sql.Timestamp, zipCode: String, countryId: Int)

  case class CountryWithZipCodes(country: Country, zipCodes: Set[ZipCode])

}
