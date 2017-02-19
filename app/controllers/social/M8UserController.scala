package controllers.social

import models.social.M8User
import play.api.libs.json.Json
import play.api.mvc.Controller
import utils._

import scala.util.{Failure, Success}

object M8UserController extends Controller {
  def listPage() = AdminAuthAction { implicit request =>
    M8User.getAll match {
      case Success(list) => Ok(views.html.social.m8UserList(list))
      case Failure(ex) => InternalServerError(ex.getMessage)
    }
  }

  def basicInfoPage(userId: Long) = AdminAuthAction { implicit request =>
    M8User.getBasicInfo(userId) match {
      case Success(Some(basicInfo)) => Ok(views.html.social.m8UserBasicInfo(M8User.basicInfoForm.fill(basicInfo)))
      case Success(None) => InternalServerError("Invalid mobile user id. Cannot retrieve the mobile user's information from database")
      case Failure(ex) => InternalServerError(ex.getMessage)
    }
  }

  def picturesPage(userId: Long) = AdminAuthAction { implicit request =>
    M8User.getPictures(userId) match {
      case Success(Some(picturesInfo)) => Ok(views.html.social.m8UserPictures(picturesInfo))
      case Success(None) => InternalServerError("Invalid mobile user id. Cannot retrieve the mobile user's information from database")
      case Failure(ex) => InternalServerError(ex.getMessage)
    }
  }

  def updatePicture(userId: Long,
                    index: Int) = AdminApiAuthAction(parse.multipartFormData) { implicit request =>
    request.body.file("photo").map {
      photo => {
        M8User.updatePicture(userId, index, photo) match {
          case Success(photoPath) => Ok(Json.obj("photoPath" -> photoPath))
          case Failure(ex) => JsonBadRequest(ex)
        }
      }
    }.getOrElse {
      JsonBadRequest("Missing photo file")
    }
  }

  def deletePicture(userId: Long,
                    index: Int) = AdminApiAuthAction { implicit request =>
    M8User.deletePicture(userId, index) match {
      case Success(_) => NoContent
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def reorganisePictures(userId: Long) = AdminAuthAction { implicit request =>
    M8User.reorganisePictures(userId) match {
      case Success(_) => Redirect(controllers.social.routes.M8UserController.picturesPage(userId))
      case Failure(ex) => InternalServerError(ex.getMessage)
    }
  }

  def saveBasicInfo(userId: Long) = AdminAuthAction { implicit request =>
    M8User.basicInfoForm.bindFromRequest.fold(
      formWithErrors => Ok(views.html.social.m8UserBasicInfo(formWithErrors)),
      basicInfo =>
        M8User.saveBasicInfo(basicInfo) match {
          case Success(_) =>
            Redirect(routes.M8UserController.basicInfoPage(userId)).flashing("message" -> "Saved!")
          case Failure(ex) =>
            val formWithData = M8User.basicInfoForm.fill(basicInfo).withGlobalError(ex.getMessage)
            Ok(views.html.social.m8UserBasicInfo(formWithData))
        }
    )
  }
}
