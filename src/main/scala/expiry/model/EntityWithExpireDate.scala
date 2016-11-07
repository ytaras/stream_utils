package expiry.model

import scala.concurrent.duration._

/**
  * Created by ytaras on 11/7/16.
  */
case class EntityWithExpireDate(id: String, timeOut: Option[FiniteDuration], seenAt: Option[Deadline]) {
  lazy val expiryAt: Option[Deadline] = seenAt.flatMap(s => timeOut.map(s + _))
  lazy val timeLeft = expiryAt.map(_.timeLeft)
  def touch(seenAt: Deadline = Deadline.now) = copy(seenAt = Some(seenAt))
  def withTimeOut(timeOut: FiniteDuration) = copy(timeOut = Some(timeOut))
}


