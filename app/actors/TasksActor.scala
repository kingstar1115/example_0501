package actors

import java.sql.Timestamp

import actors.TasksActor._
import akka.actor.Actor
import akka.pattern.pipe
import commons.enums.TaskStatuses._
import commons.monads.transformers.EitherT
import javax.inject.Inject
import models.Tables._
import play.api.Logger
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.concurrent.Execution.Implicits._
import services.TookanService
import services.TookanService.{Agent, AppointmentDetails}
import services.internal.bookings.BookingService
import services.internal.notifications.{JobNotificationData, PushNotificationService}
import slick.driver.JdbcProfile
import slick.driver.PostgresDriver.api._

import scala.concurrent.Future

class TasksActor @Inject()(tookanService: TookanService,
                           val dbConfigProvider: DatabaseConfigProvider,
                           pushNotificationService: PushNotificationService,
                           bookingService: BookingService)
  extends Actor with HasDatabaseConfigProvider[JdbcProfile] {

  private val logger = Logger(this.getClass)

  override def receive: PartialFunction[Any, Unit] = {
    case refreshTaskMessage: RefreshTaskData =>
      logger.info(s"Dispatching `Refresh` message for task ${refreshTaskMessage.jobId}")
      updateTaskData(refreshTaskMessage.jobId)
        .pipeTo(sender())
    case _ =>
      logger.warn(s"Unknown message")
  }

  private def updateTaskData(jobId: Long): Future[Either[String, AppointmentDetails]] = {

    def loadTask: Future[(TasksRow, TimeSlotsRow)] = {
      val taskSelectQuery = for {
        task <- Tasks if task.jobId === jobId
        timeSlot <- TimeSlots if timeSlot.id === task.timeSlotId
      } yield (task, timeSlot)
      db.run(taskSelectQuery.result.head)
    }

    def updateTask(task: TasksRow, appointmentDetails: AppointmentDetails, timeSlot: TimeSlotsRow,
                   agent: AgentDto): Future[Either[String, AppointmentDetails]] = {
      appointmentDetails.jobStatus match {
        case Deleted.code =>
          logger.info(s"Deleting task: ${appointmentDetails.jobId}")
          (for {
            _ <- bookingService.releaseBooking(timeSlot)
            count <- db.run(Tasks.filter(_.jobId === appointmentDetails.jobId).delete)
          } yield count)
            .map(_ => Right(appointmentDetails))
            .recover({
              case t: Throwable =>
                logger.warn(s"Failed to delete task ${appointmentDetails.jobId}", t)
                Left(t.getLocalizedMessage)
            })
        case _ =>
          saveChanges(task, appointmentDetails, agent)
            .map(_ => Right(appointmentDetails))
            .recover({
              case t: Throwable =>
                logger.warn(s"Failed to save changes for task ${appointmentDetails.jobId}", t)
                Left(t.getLocalizedMessage)
            })
      }
    }

    (for {
      tookanTask <- EitherT(tookanService.getTask(jobId))
      team <- EitherT(tookanService.getTeam)
    } yield (tookanTask, team)).inner.flatMap {
      case Right((tookanTask, team)) =>
        (for {
          agentId <- updateOrCreateAgent(tookanTask.fleetId)
          (task, timeSlot) <- loadTask
        } yield (agentId, task, timeSlot)).flatMap(result => {
          val (agentId, task, timeSlot) = result
          val agent = AgentDto(agentId, team.teamName)

          updateTask(task, tookanTask, timeSlot, agent)
        })

      case Left(error) =>
        logger.info(s"Failed to update task `$jobId` due: $error")
        Future(Left(s"${error.message}:${error.status}"))
    }
  }

  private def saveChanges(taskRow: TasksRow, appointmentDetails: AppointmentDetails, agent: AgentDto): Future[AppointmentDetails] = {
    logger.info(s"Updating task ${taskRow.jobId}: old status: ${taskRow.jobStatus}, new status: ${appointmentDetails.jobStatus}")

    val images = appointmentDetails.taskHistory.filter(_.isImageAction).map(_.description).mkString(";")
    val taskUpdateQuery = Tasks.filter(_.jobId === appointmentDetails.jobId)
      .map(job => (job.jobStatus, job.agentId, job.images, job.scheduledTime, job.jobAddress,
        job.jobPickupPhone, job.customerPhone, job.teamName, job.jobHash))
      .update((appointmentDetails.jobStatus, agent.id, Option(images), Timestamp.valueOf(appointmentDetails.getDate),
        Option(appointmentDetails.address), Option(appointmentDetails.pickupPhone), Option(appointmentDetails.customerPhone),
        Option(agent.teamName), Option(appointmentDetails.jobHash)))

    db.run(taskUpdateQuery).map { _ =>
      logger.info(s"Task ${appointmentDetails.jobId} is updated")
      if (taskRow.jobStatus != appointmentDetails.jobStatus && agent.id.isDefined) {
        sendJobStatusChangeNotification(taskRow, agent.id.get, appointmentDetails.jobStatus)
      }
      appointmentDetails
    }
  }

  private def sendJobStatusChangeNotification(job: TasksRow, agentId: Int, jobStatus: Int) = {
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

  case class RefreshTaskData(jobId: Long)

  case class AgentDto(id: Option[Int], teamName: String)

}