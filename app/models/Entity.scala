package models


trait Entity {
  def id: Int

  def createdDate: java.sql.Timestamp
}
