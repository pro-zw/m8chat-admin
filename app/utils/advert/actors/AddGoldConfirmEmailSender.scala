package utils.advert.actors

import akka.actor.Actor
import models.advert.AdvertiserAddGoldConfirm
import org.apache.commons.mail.HtmlEmail
import utils._

class AddGoldConfirmEmailSender extends Actor {
  override def receive = {
    case confirmInfo : AdvertiserAddGoldConfirm =>
      val mail = new HtmlEmail()
      mail.setFrom(EmailUtil.noReplySender, "m8chat")
      mail.addTo(confirmInfo.email)
      mail.setSubject("m8chat Advertiser Registration Confirmation")
      mail.setHtmlMsg(emails.advert.html.addGoldConfirm(RootUrl, confirmInfo).body)
      mail.setCharset("utf-8")
      EmailUtil.send(mail)
  }
}
