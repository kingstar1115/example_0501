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
  lazy val schema: profile.SchemaDescription = Array(Agents.schema, DaySlots.schema, Extras.schema, Locations.schema, PaymentDetails.schema, Services.schema, ServicesExtras.schema, Settings.schema, Tasks.schema, TaskServices.schema, TimeSlots.schema, Users.schema, Vehicles.schema).reduceLeft(_ ++ _)
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Agents
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param fleetId Database column fleet_id SqlType(int8)
   *  @param name Database column name SqlType(varchar), Length(255,true)
   *  @param fleetImage Database column fleet_image SqlType(text)
   *  @param phone Database column phone SqlType(varchar), Length(20,true)
   *  @param avrCustomerRating Database column avr_customer_rating SqlType(numeric), Default(0) */
  case class AgentsRow(id: Int, createdDate: java.sql.Timestamp, fleetId: Long, name: String, fleetImage: String, phone: String, avrCustomerRating: scala.math.BigDecimal = BigDecimal(0)) extends Entity
  /** GetResult implicit for fetching AgentsRow objects using plain SQL queries */
  implicit def GetResultAgentsRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[Long], e3: GR[String], e4: GR[scala.math.BigDecimal]): GR[AgentsRow] = GR{
    prs => import prs._
    AgentsRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[Long], <<[String], <<[String], <<[String], <<[scala.math.BigDecimal]))
  }
  /** Table description of table agents. Objects of this class serve as prototypes for rows in queries. */
  class Agents(_tableTag: Tag) extends BaseTable[AgentsRow](_tableTag, "agents") {
                def * = (id, createdDate, fleetId, name, fleetImage, phone, avrCustomerRating) <> (AgentsRow.tupled, AgentsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(fleetId), Rep.Some(name), Rep.Some(fleetImage), Rep.Some(phone), Rep.Some(avrCustomerRating)).shaped.<>({r=>import r._; _1.map(_=> AgentsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    /** Database column fleet_id SqlType(int8) */
    val fleetId: Rep[Long] = column[Long]("fleet_id")
    /** Database column name SqlType(varchar), Length(255,true) */
    val name: Rep[String] = column[String]("name", O.Length(255,varying=true))
    /** Database column fleet_image SqlType(text) */
    val fleetImage: Rep[String] = column[String]("fleet_image")
    /** Database column phone SqlType(varchar), Length(20,true) */
    val phone: Rep[String] = column[String]("phone", O.Length(20,varying=true))
    /** Database column avr_customer_rating SqlType(numeric), Default(0) */
    val avrCustomerRating: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("avr_customer_rating", O.Default(BigDecimal(0)))

    /** Uniqueness Index over (fleetId) (database name agents_fleet_id_key) */
    val index1 = index("agents_fleet_id_key", fleetId, unique=true)
              }
  /** Collection-like TableQuery object for table Agents */
  lazy val Agents = new TableQuery(tag => new Agents(tag)) with CRUDTableQuery[Agents, AgentsRow]

  /** Entity class storing rows of table DaySlots
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param date Database column date SqlType(date) */
  case class DaySlotsRow(id: Int, createdDate: java.sql.Timestamp, date: java.sql.Date) extends Entity
  /** GetResult implicit for fetching DaySlotsRow objects using plain SQL queries */
  implicit def GetResultDaySlotsRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[java.sql.Date]): GR[DaySlotsRow] = GR{
    prs => import prs._
    DaySlotsRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[java.sql.Date]))
  }
  /** Table description of table day_slots. Objects of this class serve as prototypes for rows in queries. */
  class DaySlots(_tableTag: Tag) extends BaseTable[DaySlotsRow](_tableTag, "day_slots") {
                def * = (id, createdDate, date) <> (DaySlotsRow.tupled, DaySlotsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(date)).shaped.<>({r=>import r._; _1.map(_=> DaySlotsRow.tupled((_1.get, _2.get, _3.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    /** Database column date SqlType(date) */
    val date: Rep[java.sql.Date] = column[java.sql.Date]("date")

    /** Uniqueness Index over (date) (database name day_slots_date_key) */
    val index1 = index("day_slots_date_key", date, unique=true)
              }
  /** Collection-like TableQuery object for table DaySlots */
  lazy val DaySlots = new TableQuery(tag => new DaySlots(tag)) with CRUDTableQuery[DaySlots, DaySlotsRow]

  /** Entity class storing rows of table Extras
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param name Database column name SqlType(varchar)
   *  @param description Database column description SqlType(text), Default(None)
   *  @param price Database column price SqlType(int4) */
  case class ExtrasRow(id: Int, createdDate: java.sql.Timestamp, name: String, description: Option[String] = None, price: Int) extends Entity
  /** GetResult implicit for fetching ExtrasRow objects using plain SQL queries */
  implicit def GetResultExtrasRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[String], e3: GR[Option[String]]): GR[ExtrasRow] = GR{
    prs => import prs._
    ExtrasRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[String], <<?[String], <<[Int]))
  }
  /** Table description of table extras. Objects of this class serve as prototypes for rows in queries. */
  class Extras(_tableTag: Tag) extends BaseTable[ExtrasRow](_tableTag, "extras") {
                def * = (id, createdDate, name, description, price) <> (ExtrasRow.tupled, ExtrasRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(name), description, Rep.Some(price)).shaped.<>({r=>import r._; _1.map(_=> ExtrasRow.tupled((_1.get, _2.get, _3.get, _4, _5.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    /** Database column name SqlType(varchar) */
    val name: Rep[String] = column[String]("name")
    /** Database column description SqlType(text), Default(None) */
    val description: Rep[Option[String]] = column[Option[String]]("description", O.Default(None))
    /** Database column price SqlType(int4) */
    val price: Rep[Int] = column[Int]("price")

    /** Uniqueness Index over (name) (database name extras_name_key) */
    val index1 = index("extras_name_key", name, unique=true)
              }
  /** Collection-like TableQuery object for table Extras */
  lazy val Extras = new TableQuery(tag => new Extras(tag)) with CRUDTableQuery[Extras, ExtrasRow]

  /** Entity class storing rows of table Locations
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param title Database column title SqlType(varchar), Length(255,true)
   *  @param address Database column address SqlType(varchar), Length(255,true), Default(None)
   *  @param formattedAddress Database column formatted_address SqlType(varchar), Length(255,true)
   *  @param latitude Database column latitude SqlType(numeric)
   *  @param longitude Database column longitude SqlType(numeric)
   *  @param notes Database column notes SqlType(text), Default(None)
   *  @param zipCode Database column zip_code SqlType(varchar), Length(6,true), Default(None)
   *  @param apartments Database column apartments SqlType(varchar), Length(10,true), Default(None)
   *  @param userId Database column user_id SqlType(int4) */
  case class LocationsRow(id: Int, createdDate: java.sql.Timestamp, title: String, address: Option[String] = None, formattedAddress: String, latitude: scala.math.BigDecimal, longitude: scala.math.BigDecimal, notes: Option[String] = None, zipCode: Option[String] = None, apartments: Option[String] = None, userId: Int) extends Entity
  /** GetResult implicit for fetching LocationsRow objects using plain SQL queries */
  implicit def GetResultLocationsRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[String], e3: GR[Option[String]], e4: GR[scala.math.BigDecimal]): GR[LocationsRow] = GR{
    prs => import prs._
    LocationsRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[String], <<?[String], <<[String], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<?[String], <<?[String], <<?[String], <<[Int]))
  }
  /** Table description of table locations. Objects of this class serve as prototypes for rows in queries. */
  class Locations(_tableTag: Tag) extends BaseTable[LocationsRow](_tableTag, "locations") {
                def * = (id, createdDate, title, address, formattedAddress, latitude, longitude, notes, zipCode, apartments, userId) <> (LocationsRow.tupled, LocationsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(title), address, Rep.Some(formattedAddress), Rep.Some(latitude), Rep.Some(longitude), notes, zipCode, apartments, Rep.Some(userId)).shaped.<>({r=>import r._; _1.map(_=> LocationsRow.tupled((_1.get, _2.get, _3.get, _4, _5.get, _6.get, _7.get, _8, _9, _10, _11.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
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
    /** Database column apartments SqlType(varchar), Length(10,true), Default(None) */
    val apartments: Rep[Option[String]] = column[Option[String]]("apartments", O.Length(10,varying=true), O.Default(None))
    /** Database column user_id SqlType(int4) */
    val userId: Rep[Int] = column[Int]("user_id")

    /** Foreign key referencing Users (database name locations_user_id_fkey) */
    lazy val usersFk = foreignKey("locations_user_id_fkey", userId, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
              }
  /** Collection-like TableQuery object for table Locations */
  lazy val Locations = new TableQuery(tag => new Locations(tag)) with CRUDTableQuery[Locations, LocationsRow]

  /** Entity class storing rows of table PaymentDetails
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param taskId Database column task_id SqlType(int4)
   *  @param paymentMethod Database column payment_method SqlType(varchar), Length(32,true)
   *  @param price Database column price SqlType(int4)
   *  @param tip Database column tip SqlType(int4), Default(0)
   *  @param promotion Database column promotion SqlType(int4), Default(0)
   *  @param chargeId Database column charge_id SqlType(varchar), Length(128,true), Default(None)
   *  @param createdDate Database column created_date SqlType(timestamp) */
  case class PaymentDetailsRow(id: Int, taskId: Int, paymentMethod: String, price: Int, tip: Int = 0, promotion: Int = 0, chargeId: Option[String] = None, createdDate: java.sql.Timestamp) extends Entity
  /** GetResult implicit for fetching PaymentDetailsRow objects using plain SQL queries */
  implicit def GetResultPaymentDetailsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[Option[String]], e3: GR[java.sql.Timestamp]): GR[PaymentDetailsRow] = GR{
    prs => import prs._
    PaymentDetailsRow.tupled((<<[Int], <<[Int], <<[String], <<[Int], <<[Int], <<[Int], <<?[String], <<[java.sql.Timestamp]))
  }
  /** Table description of table payment_details. Objects of this class serve as prototypes for rows in queries. */
  class PaymentDetails(_tableTag: Tag) extends BaseTable[PaymentDetailsRow](_tableTag, "payment_details") {
                def * = (id, taskId, paymentMethod, price, tip, promotion, chargeId, createdDate) <> (PaymentDetailsRow.tupled, PaymentDetailsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(taskId), Rep.Some(paymentMethod), Rep.Some(price), Rep.Some(tip), Rep.Some(promotion), chargeId, Rep.Some(createdDate)).shaped.<>({r=>import r._; _1.map(_=> PaymentDetailsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7, _8.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column task_id SqlType(int4) */
    val taskId: Rep[Int] = column[Int]("task_id")
    /** Database column payment_method SqlType(varchar), Length(32,true) */
    val paymentMethod: Rep[String] = column[String]("payment_method", O.Length(32,varying=true))
    /** Database column price SqlType(int4) */
    val price: Rep[Int] = column[Int]("price")
    /** Database column tip SqlType(int4), Default(0) */
    val tip: Rep[Int] = column[Int]("tip", O.Default(0))
    /** Database column promotion SqlType(int4), Default(0) */
    val promotion: Rep[Int] = column[Int]("promotion", O.Default(0))
    /** Database column charge_id SqlType(varchar), Length(128,true), Default(None) */
    val chargeId: Rep[Option[String]] = column[Option[String]]("charge_id", O.Length(128,varying=true), O.Default(None))
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")

    /** Foreign key referencing Tasks (database name payment_details_task_id_fkey) */
    lazy val tasksFk = foreignKey("payment_details_task_id_fkey", taskId, Tasks)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
              }
  /** Collection-like TableQuery object for table PaymentDetails */
  lazy val PaymentDetails = new TableQuery(tag => new PaymentDetails(tag)) with CRUDTableQuery[PaymentDetails, PaymentDetailsRow]

  /** Entity class storing rows of table Services
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param name Database column name SqlType(varchar)
   *  @param description Database column description SqlType(text), Default(None)
   *  @param price Database column price SqlType(int4)
   *  @param key Database column key SqlType(varchar), Length(64,true)
   *  @param deletable Database column deletable SqlType(bool), Default(true)
   *  @param isCarDependentPrice Database column is_car_dependent_price SqlType(bool), Default(false)
   *  @param enabled Database column enabled SqlType(bool), Default(Some(true))
   *  @param sequence Database column sequence SqlType(int4) */
  case class ServicesRow(id: Int, createdDate: java.sql.Timestamp, name: String, description: Option[String] = None, price: Int, key: String, deletable: Boolean = true, isCarDependentPrice: Boolean = false, enabled: Option[Boolean] = Some(true), sequence: Int) extends Entity
  /** GetResult implicit for fetching ServicesRow objects using plain SQL queries */
  implicit def GetResultServicesRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[String], e3: GR[Option[String]], e4: GR[Boolean], e5: GR[Option[Boolean]]): GR[ServicesRow] = GR{
    prs => import prs._
    ServicesRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[String], <<?[String], <<[Int], <<[String], <<[Boolean], <<[Boolean], <<?[Boolean], <<[Int]))
  }
  /** Table description of table services. Objects of this class serve as prototypes for rows in queries. */
  class Services(_tableTag: Tag) extends BaseTable[ServicesRow](_tableTag, "services") {
                def * = (id, createdDate, name, description, price, key, deletable, isCarDependentPrice, enabled, sequence) <> (ServicesRow.tupled, ServicesRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(name), description, Rep.Some(price), Rep.Some(key), Rep.Some(deletable), Rep.Some(isCarDependentPrice), enabled, Rep.Some(sequence)).shaped.<>({r=>import r._; _1.map(_=> ServicesRow.tupled((_1.get, _2.get, _3.get, _4, _5.get, _6.get, _7.get, _8.get, _9, _10.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    /** Database column name SqlType(varchar) */
    val name: Rep[String] = column[String]("name")
    /** Database column description SqlType(text), Default(None) */
    val description: Rep[Option[String]] = column[Option[String]]("description", O.Default(None))
    /** Database column price SqlType(int4) */
    val price: Rep[Int] = column[Int]("price")
    /** Database column key SqlType(varchar), Length(64,true) */
    val key: Rep[String] = column[String]("key", O.Length(64,varying=true))
    /** Database column deletable SqlType(bool), Default(true) */
    val deletable: Rep[Boolean] = column[Boolean]("deletable", O.Default(true))
    /** Database column is_car_dependent_price SqlType(bool), Default(false) */
    val isCarDependentPrice: Rep[Boolean] = column[Boolean]("is_car_dependent_price", O.Default(false))
    /** Database column enabled SqlType(bool), Default(Some(true)) */
    val enabled: Rep[Option[Boolean]] = column[Option[Boolean]]("enabled", O.Default(Some(true)))
    /** Database column sequence SqlType(int4) */
    val sequence: Rep[Int] = column[Int]("sequence")

    /** Uniqueness Index over (key) (database name services_key_key) */
    val index1 = index("services_key_key", key, unique=true)
              }
  /** Collection-like TableQuery object for table Services */
  lazy val Services = new TableQuery(tag => new Services(tag)) with CRUDTableQuery[Services, ServicesRow]

  /** Entity class storing rows of table ServicesExtras
   *  @param serviceId Database column service_id SqlType(int4)
   *  @param extraId Database column extra_id SqlType(int4) */
  case class ServicesExtrasRow(serviceId: Int, extraId: Int)
  /** GetResult implicit for fetching ServicesExtrasRow objects using plain SQL queries */
  implicit def GetResultServicesExtrasRow(implicit e0: GR[Int]): GR[ServicesExtrasRow] = GR{
    prs => import prs._
    ServicesExtrasRow.tupled((<<[Int], <<[Int]))
  }
  /** Table description of table services_extras. Objects of this class serve as prototypes for rows in queries. */
  class ServicesExtras(_tableTag: Tag) extends Table[ServicesExtrasRow](_tableTag, "services_extras") {
    def * = (serviceId, extraId) <> (ServicesExtrasRow.tupled, ServicesExtrasRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(serviceId), Rep.Some(extraId)).shaped.<>({r=>import r._; _1.map(_=> ServicesExtrasRow.tupled((_1.get, _2.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column service_id SqlType(int4) */
    val serviceId: Rep[Int] = column[Int]("service_id")
    /** Database column extra_id SqlType(int4) */
    val extraId: Rep[Int] = column[Int]("extra_id")

    /** Primary key of ServicesExtras (database name services_extras_pkey) */
    val pk = primaryKey("services_extras_pkey", (serviceId, extraId))

    /** Foreign key referencing Extras (database name services_extras_extra_id_fkey) */
    lazy val extrasFk = foreignKey("services_extras_extra_id_fkey", extraId, Extras)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing Services (database name services_extras_service_id_fkey) */
    lazy val servicesFk = foreignKey("services_extras_service_id_fkey", serviceId, Services)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
  }
  /** Collection-like TableQuery object for table ServicesExtras */
  lazy val ServicesExtras = new TableQuery(tag => new ServicesExtras(tag))

  /** Entity class storing rows of table Settings
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param key Database column key SqlType(varchar), Length(32,true)
   *  @param value Database column value SqlType(varchar), Length(64,true)
   *  @param createdDate Database column created_date SqlType(timestamp) */
  case class SettingsRow(id: Int, key: String, value: String, createdDate: java.sql.Timestamp) extends Entity
  /** GetResult implicit for fetching SettingsRow objects using plain SQL queries */
  implicit def GetResultSettingsRow(implicit e0: GR[Int], e1: GR[String], e2: GR[java.sql.Timestamp]): GR[SettingsRow] = GR{
    prs => import prs._
    SettingsRow.tupled((<<[Int], <<[String], <<[String], <<[java.sql.Timestamp]))
  }
  /** Table description of table settings. Objects of this class serve as prototypes for rows in queries. */
  class Settings(_tableTag: Tag) extends BaseTable[SettingsRow](_tableTag, "settings") {
                def * = (id, key, value, createdDate) <> (SettingsRow.tupled, SettingsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(key), Rep.Some(value), Rep.Some(createdDate)).shaped.<>({r=>import r._; _1.map(_=> SettingsRow.tupled((_1.get, _2.get, _3.get, _4.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column key SqlType(varchar), Length(32,true) */
    val key: Rep[String] = column[String]("key", O.Length(32,varying=true))
    /** Database column value SqlType(varchar), Length(64,true) */
    val value: Rep[String] = column[String]("value", O.Length(64,varying=true))
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
              }
  /** Collection-like TableQuery object for table Settings */
  lazy val Settings = new TableQuery(tag => new Settings(tag)) with CRUDTableQuery[Settings, SettingsRow]

  /** Entity class storing rows of table Tasks
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param jobId Database column job_id SqlType(int8)
   *  @param jobStatus Database column job_status SqlType(int4), Default(6)
   *  @param scheduledTime Database column scheduled_time SqlType(timestamp)
   *  @param images Database column images SqlType(text), Default(None)
   *  @param submitted Database column submitted SqlType(bool), Default(false)
   *  @param userId Database column user_id SqlType(int4)
   *  @param agentId Database column agent_id SqlType(int4), Default(None)
   *  @param vehicleId Database column vehicle_id SqlType(int4)
   *  @param jobAddress Database column job_address SqlType(varchar), Length(255,true), Default(None)
   *  @param jobPickupPhone Database column job_pickup_phone SqlType(varchar), Length(20,true), Default(None)
   *  @param customerPhone Database column customer_phone SqlType(varchar), Length(20,true), Default(None)
   *  @param teamName Database column team_name SqlType(varchar), Length(255,true), Default(None)
   *  @param hasInteriorCleaning Database column has_interior_cleaning SqlType(bool)
   *  @param latitude Database column latitude SqlType(numeric)
   *  @param longitude Database column longitude SqlType(numeric)
   *  @param timeSlotId Database column time_slot_id SqlType(int4)
   *  @param jobHash Database column job_hash SqlType(varchar), Length(64,true), Default(None)
   *  @param rating Database column rating SqlType(int4), Default(None) */
  case class TasksRow(id: Int, createdDate: java.sql.Timestamp, jobId: Long, jobStatus: Int = 6, scheduledTime: java.sql.Timestamp, images: Option[String] = None, submitted: Boolean = false, userId: Int, agentId: Option[Int] = None, vehicleId: Int, jobAddress: Option[String] = None, jobPickupPhone: Option[String] = None, customerPhone: Option[String] = None, teamName: Option[String] = None, hasInteriorCleaning: Boolean, latitude: scala.math.BigDecimal, longitude: scala.math.BigDecimal, timeSlotId: Int, jobHash: Option[String] = None, rating: Option[Int] = None) extends Entity
  /** GetResult implicit for fetching TasksRow objects using plain SQL queries */
  implicit def GetResultTasksRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[Long], e3: GR[Option[String]], e4: GR[Boolean], e5: GR[Option[Int]], e6: GR[scala.math.BigDecimal]): GR[TasksRow] = GR{
    prs => import prs._
    TasksRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[Long], <<[Int], <<[java.sql.Timestamp], <<?[String], <<[Boolean], <<[Int], <<?[Int], <<[Int], <<?[String], <<?[String], <<?[String], <<?[String], <<[Boolean], <<[scala.math.BigDecimal], <<[scala.math.BigDecimal], <<[Int], <<?[String], <<?[Int]))
  }
  /** Table description of table tasks. Objects of this class serve as prototypes for rows in queries. */
  class Tasks(_tableTag: Tag) extends BaseTable[TasksRow](_tableTag, "tasks") {
                def * = (id, createdDate, jobId, jobStatus, scheduledTime, images, submitted, userId, agentId, vehicleId, jobAddress, jobPickupPhone, customerPhone, teamName, hasInteriorCleaning, latitude, longitude, timeSlotId, jobHash, rating) <> (TasksRow.tupled, TasksRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(jobId), Rep.Some(jobStatus), Rep.Some(scheduledTime), images, Rep.Some(submitted), Rep.Some(userId), agentId, Rep.Some(vehicleId), jobAddress, jobPickupPhone, customerPhone, teamName, Rep.Some(hasInteriorCleaning), Rep.Some(latitude), Rep.Some(longitude), Rep.Some(timeSlotId), jobHash, rating).shaped.<>({r=>import r._; _1.map(_=> TasksRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7.get, _8.get, _9, _10.get, _11, _12, _13, _14, _15.get, _16.get, _17.get, _18.get, _19, _20)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    /** Database column job_id SqlType(int8) */
    val jobId: Rep[Long] = column[Long]("job_id")
    /** Database column job_status SqlType(int4), Default(6) */
    val jobStatus: Rep[Int] = column[Int]("job_status", O.Default(6))
    /** Database column scheduled_time SqlType(timestamp) */
    val scheduledTime: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("scheduled_time")
    /** Database column images SqlType(text), Default(None) */
    val images: Rep[Option[String]] = column[Option[String]]("images", O.Default(None))
    /** Database column submitted SqlType(bool), Default(false) */
    val submitted: Rep[Boolean] = column[Boolean]("submitted", O.Default(false))
    /** Database column user_id SqlType(int4) */
    val userId: Rep[Int] = column[Int]("user_id")
    /** Database column agent_id SqlType(int4), Default(None) */
    val agentId: Rep[Option[Int]] = column[Option[Int]]("agent_id", O.Default(None))
    /** Database column vehicle_id SqlType(int4) */
    val vehicleId: Rep[Int] = column[Int]("vehicle_id")
    /** Database column job_address SqlType(varchar), Length(255,true), Default(None) */
    val jobAddress: Rep[Option[String]] = column[Option[String]]("job_address", O.Length(255,varying=true), O.Default(None))
    /** Database column job_pickup_phone SqlType(varchar), Length(20,true), Default(None) */
    val jobPickupPhone: Rep[Option[String]] = column[Option[String]]("job_pickup_phone", O.Length(20,varying=true), O.Default(None))
    /** Database column customer_phone SqlType(varchar), Length(20,true), Default(None) */
    val customerPhone: Rep[Option[String]] = column[Option[String]]("customer_phone", O.Length(20,varying=true), O.Default(None))
    /** Database column team_name SqlType(varchar), Length(255,true), Default(None) */
    val teamName: Rep[Option[String]] = column[Option[String]]("team_name", O.Length(255,varying=true), O.Default(None))
    /** Database column has_interior_cleaning SqlType(bool) */
    val hasInteriorCleaning: Rep[Boolean] = column[Boolean]("has_interior_cleaning")
    /** Database column latitude SqlType(numeric) */
    val latitude: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("latitude")
    /** Database column longitude SqlType(numeric) */
    val longitude: Rep[scala.math.BigDecimal] = column[scala.math.BigDecimal]("longitude")
    /** Database column time_slot_id SqlType(int4) */
    val timeSlotId: Rep[Int] = column[Int]("time_slot_id")
    /** Database column job_hash SqlType(varchar), Length(64,true), Default(None) */
    val jobHash: Rep[Option[String]] = column[Option[String]]("job_hash", O.Length(64,varying=true), O.Default(None))
    /** Database column rating SqlType(int4), Default(None) */
    val rating: Rep[Option[Int]] = column[Option[Int]]("rating", O.Default(None))

    /** Foreign key referencing Agents (database name jobs_agent_id_fkey) */
    lazy val agentsFk = foreignKey("jobs_agent_id_fkey", agentId, Agents)(r => Rep.Some(r.id), onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing TimeSlots (database name tasks_time_slot_id_fkey) */
    lazy val timeSlotsFk = foreignKey("tasks_time_slot_id_fkey", timeSlotId, TimeSlots)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing Users (database name jobs_user_id_fkey) */
    lazy val usersFk = foreignKey("jobs_user_id_fkey", userId, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
    /** Foreign key referencing Vehicles (database name jobs_vehicle_id_fkey) */
    lazy val vehiclesFk = foreignKey("jobs_vehicle_id_fkey", vehicleId, Vehicles)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)

    /** Uniqueness Index over (jobId) (database name jobs_job_id_key) */
    val index1 = index("jobs_job_id_key", jobId, unique=true)
              }
  /** Collection-like TableQuery object for table Tasks */
  lazy val Tasks = new TableQuery(tag => new Tasks(tag)) with CRUDTableQuery[Tasks, TasksRow]

  /** Entity class storing rows of table TaskServices
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param name Database column name SqlType(varchar)
   *  @param price Database column price SqlType(int4)
   *  @param taskId Database column task_id SqlType(int4) */
  case class TaskServicesRow(id: Int, createdDate: java.sql.Timestamp, name: String, price: Int, taskId: Int) extends Entity
  /** GetResult implicit for fetching TaskServicesRow objects using plain SQL queries */
  implicit def GetResultTaskServicesRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[String]): GR[TaskServicesRow] = GR{
    prs => import prs._
    TaskServicesRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[String], <<[Int], <<[Int]))
  }
  /** Table description of table task_services. Objects of this class serve as prototypes for rows in queries. */
  class TaskServices(_tableTag: Tag) extends BaseTable[TaskServicesRow](_tableTag, "task_services") {
                def * = (id, createdDate, name, price, taskId) <> (TaskServicesRow.tupled, TaskServicesRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(name), Rep.Some(price), Rep.Some(taskId)).shaped.<>({r=>import r._; _1.map(_=> TaskServicesRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    /** Database column name SqlType(varchar) */
    val name: Rep[String] = column[String]("name")
    /** Database column price SqlType(int4) */
    val price: Rep[Int] = column[Int]("price")
    /** Database column task_id SqlType(int4) */
    val taskId: Rep[Int] = column[Int]("task_id")

    /** Foreign key referencing Tasks (database name task_services_job_id_fkey) */
    lazy val tasksFk = foreignKey("task_services_job_id_fkey", taskId, Tasks)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
              }
  /** Collection-like TableQuery object for table TaskServices */
  lazy val TaskServices = new TableQuery(tag => new TaskServices(tag)) with CRUDTableQuery[TaskServices, TaskServicesRow]

  /** Entity class storing rows of table TimeSlots
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param capacity Database column capacity SqlType(int4)
   *  @param reserved Database column reserved SqlType(int4), Default(0)
   *  @param startTime Database column start_time SqlType(time)
   *  @param endTime Database column end_time SqlType(time)
   *  @param daySlotId Database column day_slot_id SqlType(int4) */
  case class TimeSlotsRow(id: Int, createdDate: java.sql.Timestamp, capacity: Int, reserved: Int = 0, startTime: java.sql.Time, endTime: java.sql.Time, daySlotId: Int) extends Entity
  /** GetResult implicit for fetching TimeSlotsRow objects using plain SQL queries */
  implicit def GetResultTimeSlotsRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[java.sql.Time]): GR[TimeSlotsRow] = GR{
    prs => import prs._
    TimeSlotsRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[Int], <<[Int], <<[java.sql.Time], <<[java.sql.Time], <<[Int]))
  }
  /** Table description of table time_slots. Objects of this class serve as prototypes for rows in queries. */
  class TimeSlots(_tableTag: Tag) extends BaseTable[TimeSlotsRow](_tableTag, "time_slots") {
                def * = (id, createdDate, capacity, reserved, startTime, endTime, daySlotId) <> (TimeSlotsRow.tupled, TimeSlotsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(capacity), Rep.Some(reserved), Rep.Some(startTime), Rep.Some(endTime), Rep.Some(daySlotId)).shaped.<>({r=>import r._; _1.map(_=> TimeSlotsRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    /** Database column capacity SqlType(int4) */
    val capacity: Rep[Int] = column[Int]("capacity")
    /** Database column reserved SqlType(int4), Default(0) */
    val reserved: Rep[Int] = column[Int]("reserved", O.Default(0))
    /** Database column start_time SqlType(time) */
    val startTime: Rep[java.sql.Time] = column[java.sql.Time]("start_time")
    /** Database column end_time SqlType(time) */
    val endTime: Rep[java.sql.Time] = column[java.sql.Time]("end_time")
    /** Database column day_slot_id SqlType(int4) */
    val daySlotId: Rep[Int] = column[Int]("day_slot_id")

    /** Foreign key referencing DaySlots (database name time_slots_day_slot_id_fkey) */
    lazy val daySlotsFk = foreignKey("time_slots_day_slot_id_fkey", daySlotId, DaySlots)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.Cascade)

    /** Uniqueness Index over (startTime,daySlotId) (database name u_date_time) */
    val index1 = index("u_date_time", (startTime, daySlotId), unique=true)
              }
  /** Collection-like TableQuery object for table TimeSlots */
  lazy val TimeSlots = new TableQuery(tag => new TimeSlots(tag)) with CRUDTableQuery[TimeSlots, TimeSlotsRow]

  /** Entity class storing rows of table Users
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param firstName Database column first_name SqlType(varchar), Length(150,true), Default()
   *  @param lastName Database column last_name SqlType(varchar), Length(150,true), Default()
   *  @param email Database column email SqlType(varchar), Length(255,true)
   *  @param password Database column password SqlType(varchar), Length(255,true), Default(None)
   *  @param salt Database column salt SqlType(varchar), Length(255,true)
   *  @param facebookId Database column facebook_id SqlType(varchar), Length(100,true), Default(None)
   *  @param phoneCode Database column phone_code SqlType(varchar), Length(4,true)
   *  @param phone Database column phone SqlType(varchar), Length(16,true)
   *  @param userType Database column user_type SqlType(int4)
   *  @param verified Database column verified SqlType(bool), Default(false)
   *  @param code Database column code SqlType(varchar), Length(32,true), Default(None)
   *  @param profilePicture Database column profile_picture SqlType(text), Default(None)
   *  @param stripeId Database column stripe_id SqlType(varchar), Length(32,true), Default(None)
   *  @param paymentMethod Database column payment_method SqlType(varchar), Length(32,true), Default(None) */
  case class UsersRow(id: Int, createdDate: java.sql.Timestamp, firstName: String = "", lastName: String = "", email: String, password: Option[String] = None, salt: String, facebookId: Option[String] = None, phoneCode: String, phone: String, userType: Int, verified: Boolean = false, code: Option[String] = None, profilePicture: Option[String] = None, stripeId: Option[String] = None, paymentMethod: Option[String] = None) extends Entity
  /** GetResult implicit for fetching UsersRow objects using plain SQL queries */
  implicit def GetResultUsersRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[String], e3: GR[Option[String]], e4: GR[Boolean]): GR[UsersRow] = GR{
    prs => import prs._
    UsersRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[String], <<[String], <<[String], <<?[String], <<[String], <<?[String], <<[String], <<[String], <<[Int], <<[Boolean], <<?[String], <<?[String], <<?[String], <<?[String]))
  }
  /** Table description of table users. Objects of this class serve as prototypes for rows in queries. */
  class Users(_tableTag: Tag) extends BaseTable[UsersRow](_tableTag, "users") {
                def * = (id, createdDate, firstName, lastName, email, password, salt, facebookId, phoneCode, phone, userType, verified, code, profilePicture, stripeId, paymentMethod) <> (UsersRow.tupled, UsersRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(firstName), Rep.Some(lastName), Rep.Some(email), password, Rep.Some(salt), facebookId, Rep.Some(phoneCode), Rep.Some(phone), Rep.Some(userType), Rep.Some(verified), code, profilePicture, stripeId, paymentMethod).shaped.<>({r=>import r._; _1.map(_=> UsersRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6, _7.get, _8, _9.get, _10.get, _11.get, _12.get, _13, _14, _15, _16)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    /** Database column first_name SqlType(varchar), Length(150,true), Default() */
    val firstName: Rep[String] = column[String]("first_name", O.Length(150,varying=true), O.Default(""))
    /** Database column last_name SqlType(varchar), Length(150,true), Default() */
    val lastName: Rep[String] = column[String]("last_name", O.Length(150,varying=true), O.Default(""))
    /** Database column email SqlType(varchar), Length(255,true) */
    val email: Rep[String] = column[String]("email", O.Length(255,varying=true))
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
    /** Database column stripe_id SqlType(varchar), Length(32,true), Default(None) */
    val stripeId: Rep[Option[String]] = column[Option[String]]("stripe_id", O.Length(32,varying=true), O.Default(None))
    /** Database column payment_method SqlType(varchar), Length(32,true), Default(None) */
    val paymentMethod: Rep[Option[String]] = column[Option[String]]("payment_method", O.Length(32,varying=true), O.Default(None))

    /** Uniqueness Index over (email) (database name users_email_key) */
    val index1 = index("users_email_key", email, unique=true)
    /** Uniqueness Index over (facebookId) (database name users_facebook_id_key) */
    val index2 = index("users_facebook_id_key", facebookId, unique=true)
              }
  /** Collection-like TableQuery object for table Users */
  lazy val Users = new TableQuery(tag => new Users(tag)) with CRUDTableQuery[Users, UsersRow]

  /** Entity class storing rows of table Vehicles
   *  @param id Database column id SqlType(serial), AutoInc, PrimaryKey
   *  @param createdDate Database column created_date SqlType(timestamp)
   *  @param makerId Database column maker_id SqlType(varchar), Length(100,true)
   *  @param makerNiceName Database column maker_nice_name SqlType(varchar), Length(150,true)
   *  @param modelId Database column model_id SqlType(varchar), Length(255,true)
   *  @param modelNiceName Database column model_nice_name SqlType(varchar), Length(255,true)
   *  @param yearId Database column year_id SqlType(int4)
   *  @param year Database column year SqlType(int4)
   *  @param color Database column color SqlType(varchar), Length(255,true), Default(None)
   *  @param licPlate Database column lic_plate SqlType(varchar), Length(255,true), Default(None)
   *  @param userId Database column user_id SqlType(int4)
   *  @param deleted Database column deleted SqlType(bool), Default(false)
   *  @param source Database column source SqlType(varchar), Length(30,true), Default(None)
   *  @param vehicleSizeClass Database column vehicle_size_class SqlType(varchar), Length(150,true), Default(None) */
  case class VehiclesRow(id: Int, createdDate: java.sql.Timestamp, makerId: String, makerNiceName: String, modelId: String, modelNiceName: String, yearId: Int, year: Int, color: String = "None", licPlate: Option[String] = None, userId: Int, deleted: Boolean = false, source: Option[String] = None, vehicleSizeClass: Option[String] = None) extends Entity
  /** GetResult implicit for fetching VehiclesRow objects using plain SQL queries */
  implicit def GetResultVehiclesRow(implicit e0: GR[Int], e1: GR[java.sql.Timestamp], e2: GR[String], e3: GR[Option[String]], e4: GR[Boolean]): GR[VehiclesRow] = GR{
    prs => import prs._
    VehiclesRow.tupled((<<[Int], <<[java.sql.Timestamp], <<[String], <<[String], <<[String], <<[String], <<[Int], <<[Int], <<[String], <<?[String], <<[Int], <<[Boolean], <<?[String], <<?[String]))
  }
  /** Table description of table vehicles. Objects of this class serve as prototypes for rows in queries. */
  class Vehicles(_tableTag: Tag) extends BaseTable[VehiclesRow](_tableTag, "vehicles") {
                def * = (id, createdDate, makerId, makerNiceName, modelId, modelNiceName, yearId, year, color, licPlate, userId, deleted, source, vehicleSizeClass) <> (VehiclesRow.tupled, VehiclesRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = (Rep.Some(id), Rep.Some(createdDate), Rep.Some(makerId), Rep.Some(makerNiceName), Rep.Some(modelId), Rep.Some(modelNiceName), Rep.Some(yearId), Rep.Some(year), Rep.Some(color), licPlate, Rep.Some(userId), Rep.Some(deleted), source, vehicleSizeClass).shaped.<>({r=>import r._; _1.map(_=> VehiclesRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8.get, _9.get, _10, _11.get, _12.get, _13, _14)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(serial), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column created_date SqlType(timestamp) */
    val createdDate: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created_date")
    /** Database column maker_id SqlType(varchar), Length(100,true) */
    val makerId: Rep[String] = column[String]("maker_id", O.Length(100,varying=true))
    /** Database column maker_nice_name SqlType(varchar), Length(150,true) */
    val makerNiceName: Rep[String] = column[String]("maker_nice_name", O.Length(150,varying=true))
    /** Database column model_id SqlType(varchar), Length(255,true) */
    val modelId: Rep[String] = column[String]("model_id", O.Length(255,varying=true))
    /** Database column model_nice_name SqlType(varchar), Length(255,true) */
    val modelNiceName: Rep[String] = column[String]("model_nice_name", O.Length(255,varying=true))
    /** Database column year_id SqlType(int4) */
    val yearId: Rep[Int] = column[Int]("year_id")
    /** Database column year SqlType(int4) */
    val year: Rep[Int] = column[Int]("year")
    /** Database column color SqlType(varchar), Length(255,true), Default(None) */
    val color: Rep[String] = column[String]("color", O.Length(255,varying=true), O.Default("None"))
    /** Database column lic_plate SqlType(varchar), Length(255,true), Default(None) */
    val licPlate: Rep[Option[String]] = column[Option[String]]("lic_plate", O.Length(255,varying=true), O.Default(None))
    /** Database column user_id SqlType(int4) */
    val userId: Rep[Int] = column[Int]("user_id")
    /** Database column deleted SqlType(bool), Default(false) */
    val deleted: Rep[Boolean] = column[Boolean]("deleted", O.Default(false))
    /** Database column source SqlType(varchar), Length(30,true), Default(None) */
    val source: Rep[Option[String]] = column[Option[String]]("source", O.Length(30,varying=true), O.Default(None))
    /** Database column vehicle_size_class SqlType(varchar), Length(150,true), Default(None) */
    val vehicleSizeClass: Rep[Option[String]] = column[Option[String]]("vehicle_size_class", O.Length(150,varying=true), O.Default(None))

    /** Foreign key referencing Users (database name vehicles_user_id_fkey) */
    lazy val usersFk = foreignKey("vehicles_user_id_fkey", userId, Users)(r => r.id, onUpdate=ForeignKeyAction.NoAction, onDelete=ForeignKeyAction.NoAction)
              }
  /** Collection-like TableQuery object for table Vehicles */
  lazy val Vehicles = new TableQuery(tag => new Vehicles(tag)) with CRUDTableQuery[Vehicles, VehiclesRow]
}
