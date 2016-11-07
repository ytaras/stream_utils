package expiry.api

import akka.actor._
import expiry.model.EntityWithExpireDate

import scala.concurrent.duration.{Deadline, FiniteDuration}

class TimerActor extends Actor with ActorLogging {
  import TimerActor._

  def getOrCreateChild(id: String): ActorRef =
    context
      .child(id)
      .getOrElse(
        context.actorOf(ChildTimer.props(id), id)
      )

  override def receive: Receive = {
    case x: TouchEvent =>
      log.info("Received {}", x)
      getOrCreateChild(x.id) forward x
    case x: SetTimeoutDuration =>
      log.info("Received {}", x)
      getOrCreateChild(x.id) forward x
  }
}
object TimerActor {
  def props: Props = Props(new TimerActor())

  case class TouchEvent(id: String, time: Deadline)
  case class SetTimeoutDuration(id: String, timeout: FiniteDuration)
  case class TimedOut(id: String, deadline: Deadline)
}

class ChildTimer(id: String) extends Actor with ActorLogging {

  import TimerActor._
  import context.dispatcher

  override def receive: Actor.Receive = {
    case TouchEvent(`id`, time) =>
      context.become(waitForConfig(time))
    case SetTimeoutDuration(`id`, time) =>
      context.become(waitForTouch(time))
  }

  def waitForTouch(timeout: FiniteDuration): Actor.Receive = {
    case TouchEvent(`id`, seenAt) =>
      val entity = EntityWithExpireDate(id, timeout, seenAt)
      setupTimers(entity, None)
    case SetTimeoutDuration(`id`,  newTimeout) =>
      context.become(waitForTouch(newTimeout))
  }

  def waitForConfig(seenAt: Deadline): Actor.Receive = {
    case TouchEvent(`id`, time) =>
      context.become(waitForConfig(time))
    case SetTimeoutDuration(`id`, timeout) =>
      val entity = EntityWithExpireDate(id, timeout, seenAt)
      setupTimers(entity, None)
  }

  def initialized(entity: EntityWithExpireDate, previousTimer: Cancellable): Actor.Receive =  {
      case x @ TimedOut(`id`, _) =>
        log.info("Timed out {}", x)
        context.parent forward x
      case x @ TouchEvent(`id`, time) =>
        val newEntity = entity.touch(time)
        setupTimers(newEntity, Some(previousTimer))
      case SetTimeoutDuration(`id`, timeout) =>
        val newEntity = entity.withTimeOut(timeout)
        setupTimers(newEntity, Some(previousTimer))
  }

  def setupTimers(newE: EntityWithExpireDate, schedule: Option[Cancellable]) = {
    log.info("Re-setting up timer {}, {}", newE, newE.timeLeft)
    schedule.foreach(_.cancel())
    val newTimer = context.system.scheduler.scheduleOnce(newE.timeLeft, self, TimedOut(id, newE.expiryAt))
    context.become(initialized(newE, newTimer))
  }

}

object ChildTimer {
  def props(id: String) = Props(new ChildTimer(id))
}

object Main extends App {
  import scala.concurrent.duration._
  import TimerActor._
  val actorSystem = ActorSystem()
  val actor = actorSystem.actorOf(TimerActor.props)

  actor ! TouchEvent("1", Deadline.now)
  Thread.sleep(2000)
  actor ! SetTimeoutDuration("1", 1.second)

}
