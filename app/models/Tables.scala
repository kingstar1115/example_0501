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
  lazy val schema: profile.SchemaDescription = Locations.schema ++ PlayEvolutions.schema ++ TookanTasks.schema ++ Users.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Locations
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param updatedDate Database column updated_date SqlType(timestamp)
   *  @param title Database column title SqlType(varchar), Length(255,true)
   *  @param address Database column address SqlType(varchar), Length(255,true), Default(None)
   *  @param formattedAddress Database column formatted_address SqlType(varchar), Length(255,true)
   *  @param latitude Database column latitude SqlType(numeric)
   *  @param longitude Database column longitude SqlType(numeric)
   *  @param notes Database column notes SqlType(text), Default(None)
   *  @param zipCode Database column zip_code SqlType(varchar), Length(6,true), Default(None)
   *  @param appartments Database column appartments SqlType(varchar), Length(10,true), Default(None)
   *  @param userId Database column user_id SqlType(int4) */
  case class LocationsRow(id: Int, createdDate: java.sql.Timestamp, updatedDate: java.sql.Timestamp, title: String, address: Option[String] = None, formattedAddress: String, latitude: scala.math.BigDecimal, longitude: scala.math.BigDecimal, notes: Option[String] = None, zipCode: Option[String] = None, appartments: Option[String] = None, userId: Int)
  /** GetResult implicit for fetching LocationsRow objects using plain SQL queries */
  implicit def GetResultLocationsRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[String], e3: GR[Option[String]], e4: GR[scala.math.BigDecimal]): GR[LocationsRow] = GR{
    prs => import prs._
    LocationsRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[java.sql.Timestamp], <<[String], <<?[String], <<[String], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<?[String], <<?[String], <<?[String], <<[Int]))
  }
  /** Table description of table locations. Objects of this class serve as prototypes for rows in queries. */
  class Locations(_tableTag: Tag) extends Table[LocationsRow](_tableTag, "locations") {
    def * = (id, createdDate, updatedDate, title, address, formattedAddress, latitude, longitude, notes, zipCode, appartments, userId) <> (LocationsRow.tupled, LocationsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(updatedDate), Rep.Some(title), address, Rep.Some(formattedAddress), Rep.Some(latitude), Rep.Some(longitude), notes, zipCode, appartments, Rep.Some(userId)).shaped.<>({r=>import r._; _1.map(_=> LocationsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5, _6.get, _7.get, _8.get, _9, _10, _11, _12.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    /** Database column updated_date SqlType(timestamp) */
    val updatedDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("updated_date")
    /** Database column title SqlType(varchar), Length(255,true) */
    val title: Rep[String] = column[String]("title", O.Length(255,varying=true))
    /** Database column address SqlType(varchar), Length(255,true), Default(None) */
    val address: Rep[Option[String]] = column[Option[String]]("address", O.Length(255,varying=true), O.Default(None))
    /** Database column formatted_address SqlType(varchar), Length(255,true) */
    val formattedAddress: Rep[String] = column[String]("formatted_address", O.Length(255,varying=true))
    /** Database column latitude SqlType(numeric) */
    val latitude: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("latitude")
    /** Database column longitude SqlType(numeric) */
    val longitude: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("longitude")
    /** Database column notes SqlType(text), Default(None) */
    val notes: Rep[Option[String]] = column[Option[String]]("notes", O.Default(None))
    /** Database column zip_code SqlType(varchar), Length(6,true), Default(None) */
    val zipCode: Rep[Option[String]] = column[Option[String]]("zip_code", O.Length(6,varying=true), O.Default(None))
    /** Database column appartments SqlType(varchar), Length(10,true), Default(None) */
    val appartments: Rep[Option[String]] = column[Option[String]]("appartments", O.Length(10,varying=true), O.Default(None))
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

  /** Entity class storing rows of table TookanTasks
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param updatedDate Database column updated_date SqlType(timestamp)
   *  @param jobId Database column job_id SqlType(varchar), Length(12,true)
   *  @param jobStatus Database column job_status SqlType(varchar), Length(2,true)
   *  @param userId Database column user_id SqlType(int4) */
  case class TookanTasksRow(id: Int, createdDate: java.sql.Timestamp, updatedDate: java.sql.Timestamp, jobId: String, jobStatus: String, userId: Int)
  /** GetResult implicit for fetching TookanTasksRow objects using plain SQL queries */
  implicit def GetResultTookanTasksRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[String]): GR[TookanTasksRow] = GR{
    prs => import prs._
    TookanTasksRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[java.sql.Timestamp], <<[String], <<[String], <<[Int]))
  }
  /** Table description of table tookan_tasks. Objects of this class serve as prototypes for rows in queries. */
  class TookanTasks(_tableTag: Tag) extends Table[TookanTasksRow](_tableTag, "tookan_tasks") {
    def * = (id, createdDate, updatedDate, jobId, jobStatus, userId) <> (TookanTasksRow.tupled, TookanTasksRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(updatedDate), Rep.Some(jobId), Rep.Some(jobStatus), Rep.Some(userId)).shaped.<>({r=>import r._; _1.map(_=> TookanTasksRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    /** Database column updated_date SqlType(timestamp) */
    val updatedDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("updated_date")
    /** Database column job_id SqlType(varchar), Length(12,true) */
    val jobId: Rep[String] = column[String]("job_id", O.Length(12,varying=true))
    /** Database column job_status SqlType(varchar), Length(2,true) */
    val jobStatus: Rep[String] = column[String]("job_status", O.Length(2,varying=true))
    /** Database column user_id SqlType(int4) */
    val userId: Rep[Int] = column[Int]("user_id")

    /** Foreign key referencing Users (database name tookan_tasks_user_id_fkey) */
    lazy val usersFk = foreignKey("tookan_tasks_user_id_fkey", userId, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table TookanTasks */
  lazy val TookanTasks = new TableQuery(tag => new TookanTasks(tag))

  /** Entity class storing rows of table Users
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param updatedDate Database column updated_date SqlType(timestamp)
   *  @param firstName Database column first_name SqlType(varchar), Length(150,true)
   *  @param lastName Database column last_name SqlType(varchar), Length(150,true)
   *  @param email Database column email SqlType(varchar), Length(255,true), Default(None)
   *  @param password Database column password SqlType(varchar), Length(255,true), Default(None)
   *  @param salt Database column salt SqlType(varchar), Length(255,true)
   *  @param facebookId Database column facebook_id SqlType(varchar), Length(100,true), Default(None)
   *  @param phoneCode Database column phone_code SqlType(varchar), Length(4,true)
   *  @param phone Database column phone SqlType(varchar), Length(16,true)
   *  @param userType Database column user_type SqlType(int4)
   *  @param verified Database column verified SqlType(bool), Default(false)
   *  @param code Database column code SqlType(varchar), Length(32,true), Default(None)
   *  @param profilePicture Database column profile_picture SqlType(text), Default(None) */
  case class UsersRow(id: Int, createdDate: java.sql.Timestamp, updatedDate: java.sql.Timestamp, firstName: String, lastName: String, email: Option[String] = None, password: Option[String] = None, salt: String, facebookId: Option[String] = None, phoneCode: String, phone: String, userType: Int, verified: Boolean = false, code: Option[String] = None, profilePicture: Option[String] = None)
  /** GetResult implicit for fetching UsersRow objects using plain SQL queries */
  implicit def GetResultUsersRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[String], e3: GR[Option[String]], e4: GR[Boolean]): GR[UsersRow] = GR{
    prs => import prs._
    UsersRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[java.sql.Timestamp], <<[String], <<[String], <<?[String], <<?[String], <<[String], <<?[String], <<[String], <<[String], <<[Int], <<[Boolean], <<?[String], <<?[String]))
  }
  /** Table description of table users. Objects of this class serve as prototypes for rows in queries. */
  class Users(_tableTag: Tag) extends Table[UsersRow](_tableTag, "users") {
    def * = (id, createdDate, updatedDate, firstName, lastName, email, password, salt, facebookId, phoneCode, phone, userType, verified, code, profilePicture) <> (UsersRow.tupled, UsersRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(updatedDate), Rep.Some(firstName), Rep.Some(lastName), email, password, Rep.Some(salt), facebookId, Rep.Some(phoneCode), Rep.Some(phone), Rep.Some(userType), Rep.Some(verified), code, profilePicture).shaped.<>({r=>import r._; _1.map(_=> UsersRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7, _8.get, _9, _10.get, _11.get, _12.get, _13.get, _14, _15)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

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
    /** Database column salt SqlType(varchar), Length(255,true) */
    val salt: Rep[String] = column[String]("salt", O.Length(255,varying=true))
    /** Database column facebook_id SqlType(varchar), Length(100,true), Default(None) */
    val facebookId: Rep[Option[String]] = column[Option[String]]("facebook_id", O.Length(100,varying=true), O.Default(None))
    /** Database column phone_code SqlType(varchar), Length(4,true) */
    val phoneCode: Rep[String] = column[String]("phone_code", O.Length(4,varying=true))
    /** Database column phone SqlType(varchar), Length(16,true) */
    val phone: Rep[String] = column[String]("phone", O.Length(16,varying=true))
    /** Database column user_type SqlType(int4) */
    val userType: Rep[Int] = column[Int]("user_type")
    /** Database column verified SqlType(bool), Default(false) */
    val verified: Rep[Boolean] = column[Boolean]("verified", O.Default(false))
    /** Database column code SqlType(varchar), Length(32,true), Default(None) */
    val code: Rep[Option[String]] = column[Option[String]]("code", O.Length(32,varying=true), O.Default(None))
    /** Database column profile_picture SqlType(text), Default(None) */
    val profilePicture: Rep[Option[String]] = column[Option[String]]("profile_picture", O.Default(None))

    /** Uniqueness Index over (email) (database name users_email_key) */
    val index1 = index("users_email_key", email, unique=true)
    /** Uniqueness Index over (facebookId) (database name users_facebook_id_key) */
    val index2 = index("users_facebook_id_key", facebookId, unique=true)
  }
  /** Collection-like TableQuery object for table Users */
  lazy val Users = new TableQuery(tag => new Users(tag))
}
