package actors

import java.sql.Timestamp

import actors.TasksActor._
import akka.actor.{Actor, Props}
import commons.enums.TaskStatuses.Successful
import models.Tables._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import services.TookanService
import services.TookanService.{Agent, AppointmentDetails}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TasksActor(tookanService: TookanService,
                 dbConfigProvider: DatabaseConfigProvider) extends Actor {
  override def receive = {
    case t: RefreshTaskData => updateTaskData(t.jobId)
    case _ =>
  }

  private def updateTaskData(jobId: Long) = {
    Logger.info(s"Updating job with id: $jobId")
    val db = dbConfigProvider.get.db

    tookanService.getTask(jobId).map {
      case Right(task) =>
        updateOrCreateAgent(task.fleetId)
          .map { agentId =>
            val images = task.fields.images.isEmpty match {
              case false => Some(task.fields.images.mkString(";"))
              case _ => None
            }

            val taskSelectQuery = for {
              job <- Jobs if job.jobId === jobId
            } yield job
            db.run(taskSelectQuery.result.head).map { taskRow =>
              Logger.info(s"Task with id: ${taskRow.id} and jobId: $jobId updated!")
              val completed = taskRow.isTaskCompleted(task)
              val taskUpdateQuery = Jobs.filter(_.jobId === jobId)
                .map(job => (job.jobStatus, job.agentId, job.images, job.scheduledTime, job.completed))
                .update((task.jobStatus, agentId, images, Timestamp.valueOf(task.getDate), completed))
              db.run(taskUpdateQuery)
            }
          }

      case _ => Logger.info(s"Can't find job with id: $jobId")
    }
  }

  private def updateOrCreateAgent(fleetId: Option[Long]) = {
    val db = dbConfigProvider.get.db

    def saveAgent(implicit agent: Agent): Future[Option[Int]] = {
      val insertQuery = (
        Agents.map(agent => (agent.fleetId, agent.name, agent.fleetImage))
          returning Agents.map(_.id) +=(agent.fleetId, agent.name, agent.image)
        )
      db.run(insertQuery).map(id => Some(id))
    }

    def updateAgent(id: Int)(implicit agent: Agent) = {
      val updateQuery = Agents.filter(_.id === id).map(agent => (agent.name, agent.fleetImage))
        .update((agent.name, agent.image))
      db.run(updateQuery).map(updatedCount => Option(id))
    }

    def loadAgent(fleetId: Long) = {
      tookanService.listAgents.flatMap {
        case Right(agents) =>
          agents.find(agent => agent.fleetId == fleetId)
            .map { implicit agent =>
              val agentQuery = for {
                agent <- Agents if agent.fleetId === fleetId
              } yield agent
              db.run(agentQuery.result.headOption)
                .flatMap { agentOpt =>
                  agentOpt.map(existingAgent => updateAgent(existingAgent.id))
                    .getOrElse(saveAgent)
                }
            }
            .getOrElse(Future.successful(None))

        case Left(response) =>
          Logger.error(s"Can't load agents. Status: ${response.status} Message: ${response.message}")
          Future.successful(None)
      }
    }

    fleetId.map(loadAgent).getOrElse(Future.successful(None))
  }

}

object TasksActor {

  def props(tookanService: TookanService, dbConfigProvider: DatabaseConfigProvider) =
    Props(classOf[TasksActor], tookanService, dbConfigProvider)

  case class RefreshTaskData(jobId: Long)

  private implicit class JobsRowExt(job: JobsRow) {
    def isTaskCompleted(dto: AppointmentDetails) = {
      if (!job.completed) job.completed && dto.jobStatus == Successful.code else true
    }
  }

}