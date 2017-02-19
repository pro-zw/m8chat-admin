package models

import play.api.cache.Cache
import play.api.data._
import play.api.data.Forms._
import play.api.libs.Codecs
import play.api.Play.current

import scala.concurrent.duration._
import scala.language.postfixOps

case class AdminLogin(username: String,
                      plainPassword: String)

object Admin {
  val loginForm = Form(
    mapping(
      "username" -> nonEmptyText,
      "password" -> nonEmptyText
    )(AdminLogin.apply)(AdminLogin.unapply)
  )

  def login(loginInfo: AdminLogin):Option[String] = {
     if (loginInfo.username.toLowerCase == "paulofm8chat"
       && Codecs.sha1(loginInfo.plainPassword) == "81a7ab09a0acb6be368de1fed1b612f17ee094af") {
       val accessToken = java.util.UUID.randomUUID.toString
       Cache.set("accessToken", accessToken, 3 days)
       Some(accessToken)
     } else {
       None
     }
  }

  def logout() = {
    Cache.remove("accessToken")
  }

  def authenticate(accessToken: String) = {
    val accessTokenCached = Cache.getAs[String]("accessToken").getOrElse("")
    val authenticated = accessTokenCached.length > 0 && accessToken == accessTokenCached
    if (authenticated) {
      Cache.set("accessToken", accessToken, 3 days)
    }
    authenticated
  }
}
