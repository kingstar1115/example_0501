package dao.countries

import dao.countries.CountryDao._
import dao.countries.SlickCountryDao._
import javax.inject.Inject
import models.BaseTable
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits._
import slick.dbio.Effect.Read
import slick.driver.JdbcProfile

class SlickCountryDao @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)
  extends CountryDao with HasDatabaseConfigProvider[JdbcProfile] {

  import driver.api._

  override def getAllCountries: DBIOAction[Seq[Country], Streaming[Country], Read] = {
    Countries.result
  }

  override def getCountriesWithZipCodes(ids: Set[Int]): DBIOAction[Seq[CountryWithZipCodes], NoStream, Read] = {
    val countries = if (ids.isEmpty) Countries else Countries.filter(_.id.inSet(ids))
    countries.joinLeft(ZipCodes)
      .on(_.id === _.countryId).result.map(rows => {
      rows.groupBy(_._1.id).map { result =>
        val country = result._2.head._1
        CountryWithZipCodes(country, result._2.flatMap(_._2).toSet)
      }.toSeq
    })
  }

  override def getDefaultCountry: DBIOAction[Country, NoStream, Read] = {
    Countries.filter(_.default === true).result.head
  }
}

object SlickCountryDao {

  import slick.driver.PostgresDriver.api._

  class CountriesTable(tableTag: Tag) extends BaseTable[Country](tableTag, "countries") {
    override val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    override val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    val name: Rep[String] = column[String]("name", O.Length(100, varying = true))
    val default: Rep[Boolean] = column[Boolean]("default", O.Default(false))

    override def * = (id, createdDate, name, default) <> (Country.tupled, Country.unapply)
  }

  class ZipCodesTable(tableTag: Tag) extends BaseTable[ZipCode](tableTag, "zip_codes") {
    override val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    override val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    val zipCode: Rep[String] = column[String]("zip_code", O.Length(6, varying = true))
    val countryId: Rep[Int] = column[Int]("country_id")

    lazy val countriesFk = foreignKey("zip_codes_country_id_fkey", countryId, Countries)(r => r.id,
      onUpdate = ForeignKeyAction.NoAction, onDelete = ForeignKeyAction.NoAction)

    override def * = (id, createdDate, zipCode, countryId) <> (ZipCode.tupled, ZipCode.unapply)
  }

  lazy val Countries = new TableQuery(tag => new CountriesTable(tag))
  lazy val ZipCodes = new TableQuery(tag => new ZipCodesTable(tag))
}


