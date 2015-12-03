package models
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object Tables extends {
  val profile = slick.driver.PostgresDriver
} with Tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: slick.driver.JdbcProfile
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Locations.schema ++ PlayEvolutions.schema ++ Users.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Locations
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param updatedDate Database column updated_date SqlType(timestamp)
   *  @param name Database column name SqlType(varchar), Length(255,true)
   *  @param country Database column country SqlType(varchar), Length(255,true)
   *  @param state Database column state SqlType(varchar), Length(255,true), Default(None)
   *  @param city Database column city SqlType(varchar), Length(255,true)
   *  @param zipCode Database column zip_code SqlType(int4)
   *  @param userId Database column user_id SqlType(int4) */
  case class LocationsRow(id: Int, createdDate: java.sql.Timestamp, updatedDate: java.sql.Timestamp, name: String, country: String, state: Option[String] = None, city: String, zipCode: Int, userId: Int)
  /** GetResult implicit for fetching LocationsRow objects using plain SQL queries */
  implicit def GetResultLocationsRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[String], e3: GR[Option[String]]): GR[LocationsRow] = GR{
    prs => import prs._
    LocationsRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[java.sql.Timestamp], <<[String], <<[String], <<?[String], <<[String], <<[Int], <<[Int]))
  }
  /** Table description of table locations. Objects of this class serve as prototypes for rows in queries. */
  class Locations(_tableTag: Tag) extends Table[LocationsRow](_tableTag, "locations") {
    def * = (id, createdDate, updatedDate, name, country, state, city, zipCode, userId) <> (LocationsRow.tupled, LocationsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(updatedDate), Rep.Some(name), Rep.Some(country), state, Rep.Some(city), Rep.Some(zipCode), Rep.Some(userId)).shaped.<>({r=>import r._; _1.map(_=> LocationsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7.get, _8.get, _9.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    /** Database column updated_date SqlType(timestamp) */
    val updatedDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("updated_date")
    /** Database column name SqlType(varchar), Length(255,true) */
    val name: Rep[String] = column[String]("name", O.Length(255,varying=true))
    /** Database column country SqlType(varchar), Length(255,true) */
    val country: Rep[String] = column[String]("country", O.Length(255,varying=true))
    /** Database column state SqlType(varchar), Length(255,true), Default(None) */
    val state: Rep[Option[String]] = column[Option[String]]("state", O.Length(255,varying=true), O.Default(None))
    /** Database column city SqlType(varchar), Length(255,true) */
    val city: Rep[String] = column[String]("city", O.Length(255,varying=true))
    /** Database column zip_code SqlType(int4) */
    val zipCode: Rep[Int] = column[Int]("zip_code")
    /** Database column user_id SqlType(int4) */
    val userId: Rep[Int] = column[Int]("user_id")

    /** Foreign key referencing Users (database name locations_user_id_fkey) */
    lazy val usersFk = foreignKey("locations_user_id_fkey", userId, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table Locations */
  lazy val Locations = new TableQuery(tag => new Locations(tag))

  /** Entity class storing rows of table PlayEvolutions
   *  @param id Database column id SqlType(int4), PrimaryKey
   *  @param hash Database column hash SqlType(varchar), Length(255,true)
   *  @param appliedAt Database column applied_at SqlType(timestamp)
   *  @param applyScript Database column apply_script SqlType(text), Default(None)
   *  @param revertScript Database column revert_script SqlType(text), Default(None)
   *  @param state Database column state SqlType(varchar), Length(255,true), Default(None)
   *  @param lastProblem Database column last_problem SqlType(text), Default(None) */
  case class PlayEvolutionsRow(id: Int, hash: String, appliedAt: java.sql.Timestamp, applyScript: Option[String] = None, revertScript: Option[String] = None, state: Option[String] = None, lastProblem: Option[String] = None)
  /** GetResult implicit for fetching PlayEvolutionsRow objects using plain SQL queries */
  implicit def GetResultPlayEvolutionsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[java.sql.Timestamp], e3: GR[Option[String]]): GR[PlayEvolutionsRow] = GR{
    prs => import prs._
    PlayEvolutionsRow.tupled((<<[Int], <<[String], <<[java.sql.Timestamp], <<?[String], <<?[String], <<?[String], <<?[String]))
  }
  /** Table description of table play_evolutions. Objects of this class serve as prototypes for rows in queries. */
  class PlayEvolutions(_tableTag: Tag) extends Table[PlayEvolutionsRow](_tableTag, "play_evolutions") {
    def * = (id, hash, appliedAt, applyScript, revertScript, state, lastProblem) <> (PlayEvolutionsRow.tupled, PlayEvolutionsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(hash), Rep.Some(appliedAt), applyScript, revertScript, state, lastProblem).shaped.<>({r=>import r._; _1.map(_=> PlayEvolutionsRow.tupled((_1.get, _2.get, _3.get, _4, _5, _6, _7)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(int4), PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.PrimaryKey)
    /** Database column hash SqlType(varchar), Length(255,true) */
    val hash: Rep[String] = column[String]("hash", O.Length(255,varying=true))
    /** Database column applied_at SqlType(timestamp) */
    val appliedAt: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("applied_at")
    /** Database column apply_script SqlType(text), Default(None) */
    val applyScript: Rep[Option[String]] = column[Option[String]]("apply_script", O.Default(None))
    /** Database column revert_script SqlType(text), Default(None) */
    val revertScript: Rep[Option[String]] = column[Option[String]]("revert_script", O.Default(None))
    /** Database column state SqlType(varchar), Length(255,true), Default(None) */
    val state: Rep[Option[String]] = column[Option[String]]("state", O.Length(255,varying=true), O.Default(None))
    /** Database column last_problem SqlType(text), Default(None) */
    val lastProblem: Rep[Option[String]] = column[Option[String]]("last_problem", O.Default(None))
  }
  /** Collection-like TableQuery object for table PlayEvolutions */
  lazy val PlayEvolutions = new TableQuery(tag => new PlayEvolutions(tag))

  /** Entity class storing rows of table Users
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param updatedDate Database column updated_date SqlType(timestamp)
   *  @param firstName Database column first_name SqlType(varchar), Length(150,true)
   *  @param lastName Database column last_name SqlType(varchar), Length(150,true)
   *  @param email Database column email SqlType(varchar), Length(255,true), Default(None)
   *  @param password Database column password SqlType(varchar), Length(255,true), Default(None)
   *  @param salt Database column salt SqlType(varchar), Length(255,true), Default(None)
   *  @param verifyCode Database column verify_code SqlType(int4), Default(None)
   *  @param facebookId Database column facebook_id SqlType(int8), Default(None)
   *  @param phone Database column phone SqlType(varchar), Length(16,true)
   *  @param userType Database column user_type SqlType(int4)
   *  @param verified Database column verified SqlType(bool), Default(false) */
  case class UsersRow(id: Int, createdDate: java.sql.Timestamp, updatedDate: java.sql.Timestamp, firstName: String, lastName: String, email: Option[String] = None, password: Option[String] = None, salt: Option[String] = None, verifyCode: Option[Int] = None, facebookId: Option[Long] = None, phone: String, userType: Int, verified: Boolean = false)
  /** GetResult implicit for fetching UsersRow objects using plain SQL queries */
  implicit def GetResultUsersRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[String], e3: GR[Option[String]], e4: GR[Option[Int]], e5: GR[Option[Long]], e6: GR[Boolean]): GR[UsersRow] = GR{
    prs => import prs._
    UsersRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[java.sql.Timestamp], <<[String], <<[String], <<?[String], <<?[String], <<?[String], <<?[Int], <<?[Long], <<[String], <<[Int], <<[Boolean]))
  }
  /** Table description of table users. Objects of this class serve as prototypes for rows in queries. */
  class Users(_tableTag: Tag) extends Table[UsersRow](_tableTag, "users") {
    def * = (id, createdDate, updatedDate, firstName, lastName, email, password, salt, verifyCode, facebookId, phone, userType, verified) <> (UsersRow.tupled, UsersRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(updatedDate), Rep.Some(firstName), Rep.Some(lastName), email, password, salt, verifyCode, facebookId, Rep.Some(phone), Rep.Some(userType), Rep.Some(verified)).shaped.<>({r=>import r._; _1.map(_=> UsersRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7, _8, _9, _10, _11.get, _12.get, _13.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    /** Database column updated_date SqlType(timestamp) */
    val updatedDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("updated_date")
    /** Database column first_name SqlType(varchar), Length(150,true) */
    val firstName: Rep[String] = column[String]("first_name", O.Length(150,varying=true))
    /** Database column last_name SqlType(varchar), Length(150,true) */
    val lastName: Rep[String] = column[String]("last_name", O.Length(150,varying=true))
    /** Database column email SqlType(varchar), Length(255,true), Default(None) */
    val email: Rep[Option[String]] = column[Option[String]]("email", O.Length(255,varying=true), O.Default(None))
    /** Database column password SqlType(varchar), Length(255,true), Default(None) */
    val password: Rep[Option[String]] = column[Option[String]]("password", O.Length(255,varying=true), O.Default(None))
    /** Database column salt SqlType(varchar), Length(255,true), Default(None) */
    val salt: Rep[Option[String]] = column[Option[String]]("salt", O.Length(255,varying=true), O.Default(None))
    /** Database column verify_code SqlType(int4), Default(None) */
    val verifyCode: Rep[Option[Int]] = column[Option[Int]]("verify_code", O.Default(None))
    /** Database column facebook_id SqlType(int8), Default(None) */
    val facebookId: Rep[Option[Long]] = column[Option[Long]]("facebook_id", O.Default(None))
    /** Database column phone SqlType(varchar), Length(16,true) */
    val phone: Rep[String] = column[String]("phone", O.Length(16,varying=true))
    /** Database column user_type SqlType(int4) */
    val userType: Rep[Int] = column[Int]("user_type")
    /** Database column verified SqlType(bool), Default(false) */
    val verified: Rep[Boolean] = column[Boolean]("verified", O.Default(false))
  }
  /** Collection-like TableQuery object for table Users */
  lazy val Users = new TableQuery(tag => new Users(tag))
}
