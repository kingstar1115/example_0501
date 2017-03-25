package dao

trait SlickDriver {
  val profile = slick.driver.PostgresDriver
}
