package dao.tasks

import dao.{QueryObject, SlickDriver}
import models.Tables
import models.Tables._
import slick.lifted.TableQuery


object TaskQueryObject extends QueryObject[Tasks, TasksRow] with SlickDriver {

  override def query: TableQuery[Tasks] = Tasks
}
