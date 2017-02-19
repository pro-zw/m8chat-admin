package controllers.advert

import models.advert.{AdvertiserSuspend, AdvertiserAddGoldConfirm, Advertiser}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.Controller
import utils._
import utils.advert._

import scala.util.{Failure, Success}

object AdvertiserController extends Controller {
  def listPage() = AdminAuthAction { implicit request =>
    Advertiser.getAll match {
      case Success(list) => Ok(views.html.advert.advertiserList(list))
      case Failure(ex) => InternalServerError(ex.getMessage)
    }
  }

  def addGoldPage() = AdminAuthAction { implicit request =>
    Ok(views.html.advert.advertiserAddGold(Advertiser.addGoldForm))
  }

  def addGold() = AdminAuthAction { implicit request =>
    Advertiser.addGoldForm.bindFromRequest.fold (
      formWithErrors => Ok(views.html.advert.advertiserAddGold(formWithErrors)),
      addInfo =>
        Advertiser.addGold(addInfo) match {
          case Success(result: AdvertiserAddGoldConfirm) =>
            AddGoldConfirmEmailRouter ! result
            Redirect(routes.AdvertiserController.addGoldPage()).flashing("message" -> s"Created successfully with random password - ${result.password}. A confirmation email has also been sent")
          case Failure(ex) =>
            val formWithData = Advertiser.addGoldForm.fill(addInfo).withGlobalError(ex.getMessage)
            Ok(views.html.advert.advertiserAddGold(formWithData))
        }
    )
  }

  def basicInfoPage(advertiserId: Long) = AdminAuthAction { implicit request =>
    Advertiser.getBasicInfo(advertiserId) match {
      case Success(Some(basicInfo)) => Ok(views.html.advert.advertiserBasicInfo(Advertiser.basicInfoForm.fill(basicInfo)))
      case Success(None) => InternalServerError("Invalid advertiser id. Cannot retrieve the advertiser's information from database")
      case Failure(ex) => InternalServerError(ex.getMessage)
    }
  }

  def saveBasicInfo(advertiserId: Long) = AdminAuthAction { implicit request =>
    Advertiser.basicInfoForm.bindFromRequest.fold(
      formWithErrors => Ok(views.html.advert.advertiserBasicInfo(formWithErrors)),
      basicInfo =>
        Advertiser.saveBasicInfo(basicInfo) match {
          case Success(_) =>
            Redirect(routes.AdvertiserController.basicInfoPage(advertiserId)).flashing("message" -> "Saved!")
          case Failure(ex) =>
            val formWithData = Advertiser.basicInfoForm.fill(basicInfo).withGlobalError(ex.getMessage)
            Ok(views.html.advert.advertiserBasicInfo(formWithData))
        }
    )
  }

  def suspend(advertiserId: Long) = AdminApiAuthAction(parse.json) { implicit request =>
    implicit val reader = Advertiser.jsonSuspendReads

    request.body.validate[AdvertiserSuspend].toResponse {
      suspendInfo => Advertiser.suspend(advertiserId, suspendInfo) match {
        case Success(_) => NoContent
        case Failure(ex) => JsonBadRequest(ex)
      }
    }
  }

  def resume(advertiserId: Long) = AdminApiAuthAction { implicit request =>
    Advertiser.resume(advertiserId) match {
      case Success(_) => NoContent
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def planPage(advertiserId: Long) = AdminAuthAction { implicit request =>
    Advertiser.getPlan(advertiserId) match {
      case Success(Some(planInfo)) => Ok(views.html.advert.advertiserPlan(Advertiser.planForm.fill(planInfo)))
      case Success(None) => InternalServerError("Invalid advertiser id. Cannot retrieve the advertiser's information from database")
      case Failure(ex) => InternalServerError(ex.getMessage)
    }
  }

  def changePlan(advertiserId: Long) = AdminAuthAction { implicit request =>
    Advertiser.planForm.bindFromRequest.fold(
      formWithErrors => Ok(views.html.advert.advertiserPlan(formWithErrors)),
      planInfo =>
        Advertiser.changePlan(planInfo) match {
          case Success(_) =>
            Redirect(routes.AdvertiserController.planPage(advertiserId)).flashing("message" -> "Saved!")
          case Failure(ex) =>
            val formWithData = Advertiser.planForm.fill(planInfo).withGlobalError(ex.getMessage)
            Ok(views.html.advert.advertiserPlan(formWithData))
        }
    )
  }

  def businessPage(advertiserId: Long) = AdminAuthAction { implicit request =>
    Advertiser.getBusiness(advertiserId) match {
      case Success(Some(businessInfo)) => Ok(views.html.advert.advertiserBusiness(Advertiser.businessForm.fill(businessInfo)))
      case Success(None) => InternalServerError("Invalid advertiser id. Cannot retrieve the advertiser's information from database")
      case Failure(ex) => InternalServerError(ex.getMessage)
    }
  }

  def saveBusiness(advertiserId: Long) = AdminAuthAction { implicit request =>
    Advertiser.businessForm.bindFromRequest.fold(
      formWithErrors => Ok(views.html.advert.advertiserBusiness(formWithErrors)),
      businessInfo =>
        Advertiser.saveBusiness(businessInfo) match {
          case Success(_) =>
            Redirect(routes.AdvertiserController.businessPage(advertiserId)).flashing("message" -> "Saved!")
          case Failure(ex) =>
            val formWithData = Advertiser.businessForm.fill(businessInfo).withGlobalError(ex.getMessage)
            Ok(views.html.advert.advertiserBusiness(formWithData))
        }
    )
  }

  def advertPhotosPage(advertiserId: Long) = AdminAuthAction { implicit request =>
    Advertiser.getAdvertPhotos(advertiserId) match {
      case Success(Some(photosInfo)) => Ok(views.html.advert.advertiserAdvertPhotos(photosInfo))
      case Success(None) => InternalServerError("Invalid advertiser id or please input the advertiser's business information firstly")
      case Failure(ex) => InternalServerError(ex.getMessage)
    }
  }

  def updateAdvertPhoto(advertiserId: Long,
                        index: Int) = AdminApiAuthAction(parse.multipartFormData) { implicit request =>
    request.body.file("photo").map {
      photo => {
        Advertiser.updateAdvertPhotos(advertiserId, index, photo) match {
          case Success(photoPath) => Ok(Json.obj("photoPath" -> photoPath))
          case Failure(ex) => JsonBadRequest(ex)
        }
      }
    }.getOrElse {
      JsonBadRequest("Missing photo file")
    }
  }

  def deleteAdvertPhoto(advertiserId: Long,
                        index: Int) = AdminApiAuthAction { implicit request =>
    Advertiser.deleteAdvertPhoto(advertiserId, index) match {
      case Success(_) => NoContent
      case Failure(ex) => JsonBadRequest(ex)
    }
  }

  def reorganiseAdvertPhotos(advertiserId: Long) = AdminAuthAction { implicit request =>
    Advertiser.reorganiseAdvertPhotos(advertiserId) match {
      case Success(_) => Redirect(controllers.advert.routes.AdvertiserController.advertPhotosPage(advertiserId))
      case Failure(ex) => InternalServerError(ex.getMessage)
    }
  }

  def billsPage(advertiserId: Long) = AdminAuthAction { implicit request =>
    Advertiser.getBills(advertiserId) match {
      case Success((Some(name), bills)) => Ok(views.html.advert.advertiserBills(advertiserId, name, bills))
      case Success((None, _)) => InternalServerError("Invalid advertiser id. Cannot retrieve the advertiser's information from database")
      case Failure(ex) => InternalServerError(ex.getMessage)
    }
  }
}
