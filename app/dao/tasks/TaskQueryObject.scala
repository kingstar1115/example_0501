package dao.tasks

import dao.{QueryObject, SlickDriver}
import models.Tables
import models.Tables._
import slick.lifted.TableQuery


class TaskQueryObject extends QueryObject[Tasks, TasksRow] with SlickDriver {

  override def query: TableQuery[Tables.Tasks] = Tasks
}
