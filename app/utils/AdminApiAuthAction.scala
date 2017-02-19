package utils

import models.Admin
import play.api.mvc._

import scala.concurrent.Future

object AdminApiAuthAction extends ActionBuilder[Request] with ActionFilter[Request] {
  override protected def filter[A](request: Request[A]): Future[Option[Result]] = Future.successful {
    request.session.get("accessToken").map { accessToken =>
      if (Admin.authenticate(accessToken))
        None
      else
        Some(JsonErrorResult(Results.Unauthorized, "Unauthorized"))
    } getOrElse {
      Some(JsonErrorResult(Results.Unauthorized, "Unauthorized"))
    }
  }
}
