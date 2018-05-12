package actors

import java.sql.Timestamp

import actors.TasksActor._
import akka.actor.{Actor, Props}
import commons.enums.TaskStatuses._
import models.Tables._
import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.concurrent.Execution.Implicits._
import services.TookanService
import services.TookanService.{Agent, AppointmentDetails}
import services.internal.bookings.BookingService
import services.internal.notifications.{JobNotificationData, PushNotificationService}
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future

class TasksActor(tookanService: TookanService,
                 dbConfigProvider: DatabaseConfigProvider,
                 pushNotificationService: PushNotificationService,
                 bookingService: BookingService) extends Actor {

  private val logger = Logger(this.getClass)

  override def receive: PartialFunction[Any, Unit] = {
    case t: RefreshTaskData => updateTaskData(t.jobId)
    case _ =>
  }

  private def updateTaskData(jobId: Long): Unit = {
    val db = dbConfigProvider.get.db

    tookanService.getTask(jobId).map {
      case Right(tookanTask) =>
        (for {
          agentId <- updateOrCreateAgent(tookanTask.fleetId)
          team <- tookanService.getTeam
        } yield (agentId, team)).flatMap {
          case (agentId, Right(team)) =>
            val taskSelectQuery = for {
              task <- Tasks if task.jobId === jobId
              timeSlot <- TimeSlots if timeSlot.id === task.timeSlotId
            } yield (task, timeSlot)

            db.run(taskSelectQuery.result.head).map { tuple =>
              val task = tuple._1
              tookanTask.jobStatus match {
                case Deleted.code =>
                  logger.info(s"Deleting task: ${task.jobId}")
                  for {
                    _ <- bookingService.releaseBooking(tuple._2)
                    count <- db.run(Tasks.filter(_.id === task.id).delete)
                  } yield count
                case _ =>
                  update(task, tookanTask, agentId, team.teamName)
              }
            }

          case (_, Left(e)) =>
            logger.warn(s"Can't get team information for task $jobId. Message: `${e.message}`")
            Future.failed(new RuntimeException(s"${e.message}. Status: ${e.status}"))
        }

      case _ => logger.warn(s"Can't find task with id: $jobId")
    }.onFailure({
      case t: Throwable => logger.error(s"Update of $jobId task finished with error", t)
    })
  }

  private def update(taskRow: TasksRow, appointment: AppointmentDetails, agentId: Option[Int], teamName: String) = {
    logger.info(s"Updating task ${taskRow.jobId}: old status: ${taskRow.jobStatus}, new status: ${appointment.jobStatus}")

    val images = appointment.taskHistory.filter(_.isImageAction).map(_.description).mkString(";")
    val taskUpdateQuery = Tasks.filter(_.jobId === appointment.jobId)
      .map(job => (job.jobStatus, job.agentId, job.images, job.scheduledTime, job.jobAddress,
        job.jobPickupPhone, job.customerPhone, job.teamName, job.jobHash))
      .update((appointment.jobStatus, agentId, Option(images), Timestamp.valueOf(appointment.getDate), Option(appointment.address),
        Option(appointment.pickupPhone), Option(appointment.customerPhone), Option(teamName), Option(appointment.jobHash)))

    dbConfigProvider.get.db.run(taskUpdateQuery).map { _ =>
      logger.info(s"Task ${appointment.jobId} is updated")
      if (taskRow.jobStatus != appointment.jobStatus && agentId.isDefined) {
        sendJobStatusChangeNotification(taskRow, agentId.get, appointment.jobStatus)
      }
    }
  }

  private def sendJobStatusChangeNotification(job: TasksRow, agentId: Int, jobStatus: Int) = {
    val db = dbConfigProvider.get.db
    val agentQuery = for {
      agent <- Agents if agent.id === agentId
    } yield agent.name
    db.run(agentQuery.result.head).map { agentName =>
      val data = JobNotificationData(job.jobId, agentName, jobStatus, job.userId)
      pushNotificationService.getUserDeviceTokens(job.userId)
        .foreach { token =>
          jobStatus match {
            case x if x == Started.code =>
              pushNotificationService.sendJobStartedNotification(data, token)
            case x if x == InProgress.code =>
              pushNotificationService.sendJobInProgressNotification(data, token)
            case x if x == Successful.code =>
              pushNotificationService.sendJobCompleteNotification(data, token)
            case _ =>
          }
        }
    }
  }

  private def updateOrCreateAgent(fleetId: Option[Long]) = {
    val db = dbConfigProvider.get.db

    def saveAgent(agent: Agent): Future[Option[Int]] = {
      val insertQuery = (
        Agents.map(agent => (agent.fleetId, agent.name, agent.fleetImage, agent.phone, agent.avrCustomerRating))
          returning Agents.map(_.id) += (agent.fleetId, agent.name.trim, agent.image, agent.phone, agent.avrCustomerRating)
        )
      db.run(insertQuery).map(id => Some(id))
    }

    def updateAgent(id: Int, agent: Agent) = {
      val updateQuery = Agents.filter(_.id === id)
        .map(agent => (agent.name, agent.fleetImage, agent.phone, agent.avrCustomerRating))
        .update((agent.name.trim, agent.image, agent.phone, agent.avrCustomerRating))
      db.run(updateQuery).map(_ => Option(id))
    }

    def loadAgent(fleetId: Long) = {
      tookanService.getAgent(fleetId).flatMap {
        case Right(agent) =>
          val agentQuery = for {
            agent <- Agents if agent.fleetId === fleetId
          } yield agent
          db.run(agentQuery.result.headOption)
            .flatMap { agentOpt =>
              agentOpt.map(persistedAgent => updateAgent(persistedAgent.id, agent))
                .getOrElse(saveAgent(agent))
            }

        case Left(response) =>
          logger.warn(s"Can't load agents. Status: ${response.status} Message: ${response.message}")
          Future(None)
      }
    }

    fleetId.map(loadAgent).getOrElse(Future(None))
  }

}

object TasksActor {

  def props(tookanService: TookanService, dbConfigProvider: DatabaseConfigProvider,
            pushNotificationService: PushNotificationService, bookingService: BookingService) =
    Props(new TasksActor(tookanService, dbConfigProvider, pushNotificationService, bookingService))

  case class RefreshTaskData(jobId: Long)

}