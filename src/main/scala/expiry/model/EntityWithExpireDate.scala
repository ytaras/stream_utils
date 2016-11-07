package expiry.model

import scala.concurrent.duration._

/**
  * Created by ytaras on 11/7/16.
  */
case class EntityWithExpireDate(id: String, timeOut: FiniteDuration, seenAt: Deadline) {
  lazy val expiryAt: Deadline = seenAt + timeOut
  lazy val timeLeft = expiryAt.timeLeft
  def touch(seenAt: Deadline = Deadline.now) = copy(seenAt = seenAt)
  def withTimeOut(timeOut: FiniteDuration) = copy(timeOut = timeOut)
}


