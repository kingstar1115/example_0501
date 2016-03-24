package actors

import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import actors.TasksActor.RefreshTaskData
import akka.actor.{Actor, Props}
import models.Tables._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import services.TookanService
import services.TookanService.Agent
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
    val db = dbConfigProvider.get.db

    tookanService.getTask(jobId).map {
      case Right(task) =>
        updateOrCreateAgent(task.fleetId)
          .map { agentId =>
            val images = task.fields.images.isEmpty match {
              case false => Some(task.fields.images.mkString(";"))
              case _ => None
            }

            val taskUpdateQuery = Jobs.filter(_.jobId === jobId)
              .map(job => (job.jobStatus, job.agentId, job.images, job.scheduledTime))
              .update((task.jobStatus, agentId, images, Timestamp.valueOf(task.getDate)))
            db.run(taskUpdateQuery)
          }

      case _ =>
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

}