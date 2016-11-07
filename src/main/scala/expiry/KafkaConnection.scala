package expiry

import akka.actor.ActorSystem
import akka.kafka.{ConsumerSettings, Subscriptions}
import akka.kafka.scaladsl.Consumer
import expiry.actor.EntityTimerFrontendActor.TouchEvent
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, LongDeserializer, StringDeserializer}

import scala.concurrent.duration._
import scala.concurrent.duration.Deadline

/**
  * Created by ytaras on 07.11.16.
  */
class KafkaConnection {
  val actorSystem = ActorSystem()
  val consumerSettings = ConsumerSettings(actorSystem, new StringDeserializer, new LongDeserializer)
  Consumer
    .committableSource(consumerSettings, Subscriptions.topics("timer_events"))
    .map { msg =>
      (TouchEvent(msg.record.key(), Deadline.apply(Duration(msg.record.value, SECONDS))), msg.committableOffset)
    }

}
