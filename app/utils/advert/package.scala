package utils

import akka.actor.Props
import akka.routing.RoundRobinPool
import play.api.libs.concurrent.Akka
import play.api.Play.current
import utils.advert.actors.AddGoldConfirmEmailSender

package object advert {
  val AddGoldConfirmEmailRouter = Akka.system.actorOf(RoundRobinPool(2).props(Props[AddGoldConfirmEmailSender]), "advert.AddGoldConfirmEmailRouter")
}
