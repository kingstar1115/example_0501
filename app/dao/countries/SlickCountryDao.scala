package dao.countries

import javax.inject.Inject
import models.{BaseTable, Country}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.driver.JdbcProfile

class SlickCountryDao @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
  extends CountryDao with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  override def getAllCountries: StreamingDBIO[Seq[Country], Country] = {
    Countries.result
  }

  class CountriesTable(tableTag: Tag) extends BaseTable[Country](tableTag, "countries") {
    override val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    override val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    val name: Rep[String] = column[String]("name", O.Length(100, varying = true))

    override def * = (id, createdDate, name) <> (Country.tupled, Country.unapply)
  }

  lazy val Countries = new TableQuery(tag => new CountriesTable(tag))
}


