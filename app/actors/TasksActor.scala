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
import services.TookanService.{Agent, AppointmentDetails, Team}
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
      refreshTask(refreshTaskMessage.jobId)
        .pipeTo(sender())
    case _ =>
      logger.warn(s"Unknown message")
  }

  private def refreshTask(jobId: Long): Future[Either[String, AppointmentDetails]] = {
    (for {
      taskInfo <- EitherT(loadTask(jobId))
      appointmentInfo <- EitherT(loadAppointment(taskInfo))
    } yield AppointmentContext(taskInfo, appointmentInfo)).inner.flatMap({
      case Right(appointmentContext) =>
        val appointment = appointmentContext.appointmentInfo.appointment

        updateOrCreateAgent(appointment.fleetId)
          .flatMap(agentId => {
            val agent = AgentDto(agentId, appointmentContext.appointmentInfo.team.teamName)
            deleteOrUpdateTask(appointmentContext, agent)
          })
      case Left(message) =>
        Future(Left(message))
    })
  }

  private def loadTask(jobId: Long): Future[Either[String, TaskInfo]] = {
    val taskSelectQuery = for {
      task <- Tasks if task.jobId === jobId
      timeSlot <- TimeSlots if timeSlot.id === task.timeSlotId
    } yield (task, timeSlot)
    db.run(taskSelectQuery.result.headOption)
      .map({
        case Some((task, timeSlot)) =>
          Right(TaskInfo(task, timeSlot))
        case None =>
          logger.info(s"Task `$jobId` is not exists or created by anonymous user")
          Left(s"Task `$jobId` is not exists or created by anonymous user")
      })
  }

  private def loadAppointment(task: TaskInfo): Future[Either[String, AppointmentInfo]] = {
    (for {
      appointmentDetails <- EitherT(tookanService.getTask(task.task.jobId))
      team <- EitherT(tookanService.getTeam)
    } yield (appointmentDetails, team)).inner.map {
      case Right((appointmentDetails, team)) =>
        Right(AppointmentInfo(appointmentDetails, team))
      case Left(error) =>
        logger.info(s"Failed to update task `${task.task.jobId}` due: $error")
        Left(s"${error.message}:${error.status}")
    }
  }

  private def updateOrCreateAgent(fleetId: Option[Long]): Future[Option[Int]] = {

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

  private def deleteOrUpdateTask(appointmentContext: AppointmentContext, agent: AgentDto): Future[Either[String, AppointmentDetails]] = {
    appointmentContext.appointmentInfo.appointment.jobStatus match {
      case Deleted.code =>
        logger.info(s"Deleting task: ${appointmentContext.appointmentInfo.appointment.jobId}")
        (for {
          _ <- bookingService.releaseBooking(appointmentContext.taskInfo.timeSlot)
          count <- db.run(Tasks.filter(_.jobId === appointmentContext.appointmentInfo.appointment.jobId).delete)
        } yield count)
          .map(_ => Right(appointmentContext.appointmentInfo.appointment))
          .recover({
            case t: Throwable =>
              logger.warn(s"Failed to delete task ${appointmentContext.appointmentInfo.appointment.jobId}", t)
              Left(t.getLocalizedMessage)
          })
      case _ =>
        updateTask(appointmentContext, agent)
          .map(_ => Right(appointmentContext.appointmentInfo.appointment))
          .recover({
            case t: Throwable =>
              logger.warn(s"Failed to save changes for task ${appointmentContext.appointmentInfo.appointment.jobId}", t)
              Left(t.getLocalizedMessage)
          })
    }
  }

  private def updateTask(appointmentContext: AppointmentContext, agent: AgentDto): Future[AppointmentDetails] = {
    val task = appointmentContext.taskInfo.task
    val appointmentDetails = appointmentContext.appointmentInfo.appointment
    logger.info(s"Updating task ${task.jobId}: old status: ${task.jobStatus}, new status: ${appointmentDetails.jobStatus}")

    val images = appointmentDetails.taskHistory.filter(_.isImageAction).map(_.description).mkString(";")
    val taskUpdateQuery = Tasks.filter(_.jobId === appointmentDetails.jobId)
      .map(job => (job.jobStatus, job.agentId, job.images, job.scheduledTime, job.jobAddress,
        job.jobPickupPhone, job.customerPhone, job.teamName, job.jobHash))
      .update((appointmentDetails.jobStatus, agent.id, Option(images), Timestamp.valueOf(appointmentDetails.getDate),
        Option(appointmentDetails.address), Option(appointmentDetails.pickupPhone), Option(appointmentDetails.customerPhone),
        Option(agent.teamName), Option(appointmentDetails.jobHash)))

    db.run(taskUpdateQuery).map { _ =>
      logger.info(s"Task ${appointmentDetails.jobId} is updated")
      if (task.jobStatus != appointmentDetails.jobStatus && agent.id.isDefined) {
        sendJobStatusChangeNotification(task, agent.id.get, appointmentDetails.jobStatus)
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

}

object TasksActor {

  case class RefreshTaskData(jobId: Long)

  case class TaskInfo(task: TasksRow, timeSlot: TimeSlotsRow)

  case class AppointmentInfo(appointment: AppointmentDetails, team: Team)

  case class AppointmentContext(taskInfo: TaskInfo, appointmentInfo: AppointmentInfo)

  case class AgentDto(id: Option[Int], teamName: String)

}