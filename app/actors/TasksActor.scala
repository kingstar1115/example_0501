package actors

import java.sql.Timestamp

import actors.TasksActor._
import akka.actor.{Actor, Props}
import commons.enums.TaskStatuses._
import models.Tables._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import services.TookanService
import services.TookanService.Agent
import services.internal.notifications.{JobNotificationData, PushNotificationService}
import slick.driver.PostgresDriver.api._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TasksActor(tookanService: TookanService,
                 dbConfigProvider: DatabaseConfigProvider,
                 pushNotificationService: PushNotificationService) extends Actor {

  override def receive = {
    case t: RefreshTaskData => updateTaskData(t.jobId)
    case _ =>
  }

  private def updateTaskData(jobId: Long) = {
    val db = dbConfigProvider.get.db

    tookanService.getTask(jobId).map {
      case Right(task) =>
        updateOrCreateAgent(task.fleetId)
          .flatMap { agentId =>
            tookanService.getTeam.flatMap {
              case Left(e) =>
                Logger.debug("Can't get team information")
                Future.failed(new RuntimeException(s"${e.message}. Status: ${e.status}"))
              case Right(team) =>
                val images = task.taskHistory.filter(_.isImageAction).map(_.description).mkString(";")
                val taskSelectQuery = for {
                  job <- Jobs if job.jobId === jobId
                } yield job

                db.run(taskSelectQuery.result.head).map { taskRow =>
                  Logger.debug(s"Old status: ${taskRow.jobStatus}. New status: ${task.jobStatus}")
                  val taskUpdateQuery = Jobs.filter(_.jobId === jobId)
                    .map(job => (job.jobStatus, job.agentId, job.images, job.scheduledTime, job.jobAddress,
                      job.jobPickupPhone, job.customerPhone, job.teamName))
                    .update((task.jobStatus, agentId, Option(images), Timestamp.valueOf(task.getDate), Option(task.address),
                      Option(task.pickupPhone), Option(task.customerPhone), Option(team.teamName)))
                  db.run(taskUpdateQuery)
                    .map { _ =>
                      Logger.debug(s"Task with id: ${taskRow.id} and jobId: $jobId updated!")
                      if (taskRow.jobStatus != task.jobStatus && agentId.isDefined) {
                        sendJobStatusChangeNotification(taskRow, agentId.get, task.jobStatus)
                      }
                    }
                }
            }
          }

      case _ => Logger.debug(s"Can't find job with id: $jobId")
    }
  }

  def sendJobStatusChangeNotification(job: JobsRow, agentId: Int, jobStatus: Int) = {
    val db = dbConfigProvider.get.db
    val agentQuery = for {
      agent <- Agents if agent.id === agentId
    } yield agent.name
    db.run(agentQuery.result.head).map { agentName =>
      val data = new JobNotificationData(job.jobId, agentName, jobStatus, job.userId)
      pushNotificationService.getUserDeviceTokens(job.userId)
        .foreach { token =>
          jobStatus match {
            case x if x == Accepted.code =>
              pushNotificationService.sendJobAcceptedNotification(data, token)
            case x if x == Started.code =>
              pushNotificationService.sendJobStartedNotification(data, token)
            case x if x == InProgress.code =>
              pushNotificationService.sendJobInProgressNotification(data, token)
            case x if x == Successful.code =>
              pushNotificationService.sendJobAcceptedNotification(data, token)
            case _ =>
          }
        }
    }
  }

  private def updateOrCreateAgent(fleetId: Option[Long]) = {
    val db = dbConfigProvider.get.db

    def saveAgent(implicit agent: Agent): Future[Option[Int]] = {
      val insertQuery = (
        Agents.map(agent => (agent.fleetId, agent.name, agent.fleetImage, agent.phone))
          returning Agents.map(_.id) +=(agent.fleetId, agent.name, agent.image, agent.phone)
        )
      db.run(insertQuery).map(id => Some(id))
    }

    def updateAgent(id: Int)(implicit agent: Agent) = {
      val updateQuery = Agents.filter(_.id === id).map(agent => (agent.name, agent.fleetImage, agent.phone))
        .update((agent.name, agent.image, agent.phone))
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

  def props(tookanService: TookanService, dbConfigProvider: DatabaseConfigProvider,
            pushNotificationService: PushNotificationService) =
    Props(classOf[TasksActor], tookanService, dbConfigProvider, pushNotificationService)

  case class RefreshTaskData(jobId: Long)

}