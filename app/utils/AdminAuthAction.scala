package utils

import controllers.routes
import models.Admin
import play.api.mvc._


import scala.concurrent.Future

object AdminAuthAction extends ActionBuilder[Request] with ActionFilter[Request] {
  override protected def filter[A](request: Request[A]): Future[Option[Result]] = Future.successful {
    request.session.get("accessToken").map { accessToken =>
      if (Admin.authenticate(accessToken))
        None
      else
        Some(Results.Redirect(routes.Application.login()))
    } getOrElse {
      Some(Results.Redirect(routes.Application.login()))
    }
  }
}
