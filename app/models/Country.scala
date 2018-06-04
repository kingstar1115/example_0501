package models

import slick.driver.PostgresDriver.api._

case class Country(id: Int, createdDate: java.sql.Timestamp, name: String)

class CountriesTable(tableTag: Tag) extends BaseTable[Country](tableTag, "countries") {
  override val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
  override val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
  val name: Rep[String] = column[String]("name", O.Length(100, varying = true))

  override def * = (id, createdDate, name) <> (Country.tupled, Country.unapply)
}

object CountriesTable {
  lazy val Countries = new TableQuery(tag => new CountriesTable(tag))
}

case class ZipCode(id: Int, createdDate: java.sql.Timestamp, zipCode: String, countryId: Int)

class ZipCodesTable(tableTag: Tag) extends BaseTable[ZipCode](tableTag, "zip_codes") {
  override val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
  override val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
  val zipCode: Rep[String] = column[String]("zip_code", O.Length(6, varying = true))
  val countryId: Rep[Int] = column[Int]("country_id")

  lazy val countriesFk = foreignKey("zip_codes_country_id_fkey", countryId, CountriesTable.Countries)(r => r.id,
    onUpdate = ForeignKeyAction.NoAction, onDelete = ForeignKeyAction.NoAction)

  override def * = (id, createdDate, zipCode, countryId) <> (ZipCode.tupled, ZipCode.unapply)
}

object ZipCodesTable {
  lazy val ZipCodes = new TableQuery(tag => new ZipCodesTable(tag))
}