package expiry.actor

import akka.actor._
import akka.persistence.{PersistentActor, RecoveryCompleted}
import akka.stream.actor.{ActorSubscriber, MaxInFlightRequestStrategy, RequestStrategy}
import expiry.model.EntityWithExpireDate

import scala.concurrent.duration.{Deadline, FiniteDuration}

class EntityTimerFrontendActor extends Actor with ActorLogging {
  import EntityTimerFrontendActor._

  var inFlight = 0

  def getOrCreateChild(id: String): ActorRef =
    context
      .child(id)
      .getOrElse(
        context.actorOf(EntityTimerActor.props(id), id)
      )

  override def receive: Receive = {
    case x: TouchEvent =>
      log.info("Received {}", x)
      getOrCreateChild(x.id) forward x
    case x: SetTimeoutDuration =>
      log.info("Received {}", x)
      getOrCreateChild(x.id) forward x
    case x: TimedOut =>
      log.error("TIMED! {}", x)
  }

  override protected def requestStrategy: RequestStrategy = new MaxInFlightRequestStrategy() {
    override def inFlightInternally: Int = inFlight
  }
}
object EntityTimerFrontendActor {
  def props: Props = Props(new EntityTimerFrontendActor())

  case class TouchEvent(id: String, time: Deadline)
  case class SetTimeoutDuration(id: String, timeout: FiniteDuration)
  case class TimedOut(id: String, deadline: Deadline)
}

class EntityTimerActor(id: String) extends PersistentActor with ActorLogging {

  import EntityTimerFrontendActor._
  import context.dispatcher

  var state = EntityWithExpireDate(id, None, None)
  var timer: Option[Cancellable] = None
  var fired: Boolean = false

  def setupTimers(updater: EntityWithExpireDate => EntityWithExpireDate) = {
    fired = false
    state = updater(state)
    log.info("Re-setting up timer {}, {}", state, state.timeLeft)
    timer.foreach(_.cancel())
    timer = for {
      at <- state.expiryAt
      left <- state.timeLeft
    } yield context.system.scheduler.scheduleOnce(left, self, TimedOut(id, at))
  }

  override def receiveRecover: Receive = {
    case x: TouchEvent =>
      fired = false
      state = state.touch(x.time)
    case x: SetTimeoutDuration =>
      fired = false
      state = state.withTimeOut(x.timeout)
    case _: TimedOut => fired = true
    case RecoveryCompleted =>
      if(!fired) setupTimers(identity)
  }


  override def receiveCommand: Receive = {
    case x @ TouchEvent(`id`, seenAt) => persist(x) { _ =>
      setupTimers(_.touch(seenAt))
    }
    case x @ SetTimeoutDuration(`id`, timeout) => persist(x) { _ =>
      setupTimers(_.withTimeOut(timeout))
    }
    case x: TimedOut => persist(x) { _ =>
      fired = true
      context.parent forward x
    }
  }

  override def persistenceId: String = id
}

object EntityTimerActor {
  def props(id: String) = Props(new EntityTimerActor(id))
}

object Main extends App {
  import scala.concurrent.duration._
  import EntityTimerFrontendActor._
  val actorSystem = ActorSystem()
  val actor = actorSystem.actorOf(EntityTimerFrontendActor.props)

  actor ! TouchEvent("1", Deadline.now)
  Thread.sleep(2000)
//  actor ! SetTimeoutDuration("1", 5.second)

}
