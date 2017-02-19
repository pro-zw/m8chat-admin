package controllers

import models.{Admin, Dashboard}
import play.api.mvc._
import utils.AdminAuthAction

import scala.util.{Success, Failure}

object Application extends Controller {
  def loginPage = Action { implicit request =>
    Ok(views.html.login(Admin.loginForm))
  }

  def login = Action { implicit request =>
    Admin.loginForm.bindFromRequest.fold (
      formWithErrors => Ok(views.html.login(formWithErrors)),
      loginInfo =>
        Admin.login(loginInfo) match {
          case Some(accessToken) if accessToken.length > 0 =>
            Redirect(routes.Application.index())
              .withSession(request.session + ("accessToken" -> accessToken))
          case _ =>
            val formWithData = Admin.loginForm.fill(loginInfo).withGlobalError("Wrong username and/or password")
            Ok(views.html.login(formWithData))
              .withSession(request.session - "accessToken")
        }
    )
  }

  def logout = Action { implicit request =>
    Admin.logout()
    Redirect(routes.Application.login())
      .withSession(request.session - "accessToken")
  }

  def index = AdminAuthAction { implicit request =>
    Dashboard.getStatistics match {
      case Success(result) => Ok(views.html.index(result))
      case Failure(ex) => InternalServerError(ex.getMessage)
    }
  }
}