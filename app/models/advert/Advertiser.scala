package models.advert

import java.io.File
import java.nio.file.Paths
import java.util.Locale

import org.apache.commons.io.FilenameUtils
import org.joda.time.DateTime
import anorm._
import org.joda.time.format.DateTimeFormat
import play.api.Logger
import play.api.data.Forms.email
import play.api.data.Forms.optional
import play.api.data._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats
import play.api.db.DB
import play.api.Play.current
import play.api.libs.{Files, Codecs}
import play.api.libs.json.Reads.maxLength
import play.api.libs.json._
import play.api.mvc.MultipartFormData
import scala.util.{Random, Try}
import scala.util.matching.Regex
import utils._

case class AdvertiserListItem(advertiserId: Long,
                              name: String,
                              companyName: String,
                              email: String,
                              activeUtil: Option[DateTime],
                              planName: String,
                              price: Option[BigDecimal],
                              status: String)

case class AdvertiserAddGold(name: String,
                             companyName: String,
                             email: String,
                             price: BigDecimal)

case class AdvertiserAddGoldConfirm(advertiserId: Long,
                                    name: String,
                                    email: String,
                                    emailConfirmToken: String,
                                    price: BigDecimal,
                                    password: String)

case class AdvertiserBasicInfo(advertiserId: Long,
                               name: String,
                               companyName: String,
                               email: String,
                               status: String,
                               createdAt: DateTime,
                               activeUtil: Option[DateTime],
                               suspendedReason: Option[String],
                               adminNote: Option[String])

case class AdvertiserSuspend(reason: String)

case class AdvertiserPlan(advertiserId: Long,
                          name: String,
                          planName: String,
                          price: BigDecimal,
                          photoLimit: Int,
                          balance: BigDecimal,
                          balanceChargedAt: Option[DateTime])

case class AdvertiserBusiness(advertiserId: Long,
                              advertId: Long,
                              name: String,
                              businessName: String,
                              contactNumber: Option[String],
                              website: Option[String],
                              address: String,
                              latitude: Double,
                              longitude: Double,
                              description: String,
                              displayedTimes: Long)

case class AdvertiserAdvertPhotos(advertiserId: Long,
                                  name: String,
                                  photos: Seq[String])

case class AdvertiserBill(billId: Long,
                          issuedAt: DateTime,
                          paidAt: Option[DateTime],
                          expiringAt: Option[DateTime],
                          canceledAt: Option[DateTime],
                          amount: BigDecimal,
                          paymentId: Option[String],
                          status: String)

object Advertiser {
  val validPlanNames = List("bronze", "silver", "gold")

  val jsonSuspendReads:Reads[AdvertiserSuspend] =
    (__ \ "reason").read[String](maxLength[String](120)).map(p => AdvertiserSuspend(p))

  val addGoldForm = Form(
    mapping(
      "name" -> nonEmptyText(maxLength = 80),
      "companyName" -> nonEmptyText(maxLength = 100),
      "email" -> nonEmptyText(maxLength = 180)
        .verifying("Invalid email address",
          email => email match {
            case EmailMask(_) => true
            case _ => false
          })
        .verifying("Email is already registered",
          email => Advertiser.emailNotExists(email).getOrElse(false)),
      "price" -> of(moneyMapping)
        .verifying("Price must be greater than or equal to zero",
          price => price >= 0.0)
    )(AdvertiserAddGold.apply)(AdvertiserAddGold.unapply)
  )

  val basicInfoForm = Form(
    mapping(
      "advertiserId" -> longNumber(min = 1L),
      "name" -> nonEmptyText(maxLength = 80),
      "companyName" -> nonEmptyText(maxLength = 100),
      "email" -> nonEmptyText(maxLength = 180)
        .verifying("Invalid email address",
          email => email match {
            case EmailMask(_) => true
            case _ => false
          }),
      "status" -> nonEmptyText(),
      "createdAt" -> of(readonlyDateTimeMapping),
      "activeUtil" -> optional(of(readonlyDateTimeMapping)),
      "suspendedReason" -> optional(text(maxLength = 120)),
      "adminNote" -> optional(text)
    )(AdvertiserBasicInfo.apply)(AdvertiserBasicInfo.unapply)
  )

  val planForm = Form(
    mapping(
      "advertiserId" -> longNumber(min = 1L),
      "name" -> nonEmptyText(maxLength = 80),
      "planName" -> nonEmptyText()
        .verifying("Invalid plan name",
          planName => { validPlanNames.contains(planName) }
        ),
      "price" -> of(moneyMapping)
        .verifying("Price must be larger than zero",
          price => price > 0.0),
      "photoLimit" -> number(min = 1, max = 10),
      "balance" -> of(moneyMapping),
      "balanceChargedAt" -> optional(of(readonlyDateTimeMapping))
    )(AdvertiserPlan.apply)(AdvertiserPlan.unapply)
  )

  val businessForm = Form(
    mapping(
      "advertiserId" -> longNumber(min = 1L),
      "advertId" -> longNumber(min = 0L),
      "name" -> nonEmptyText(maxLength = 80),
      "businessName" -> nonEmptyText(maxLength = 100),
      "contactNumber" -> optional(text(maxLength = 60)),
      "website" -> optional(text),
      "address" -> nonEmptyText(),
      "latitude" -> of(Formats.doubleFormat)
        .verifying("Invalid latitude value",
          latitude => { latitude >= -90.0 && latitude <= 90.0 }
        ),
      "longitude" -> of(Formats.doubleFormat)
        .verifying("Invalid longitude value",
          longitude => { longitude >= -180.0 && longitude <= 180.0 }
        ),
      "description" -> nonEmptyText(maxLength = 500),
      "displayedTimes" -> longNumber()
    )(AdvertiserBusiness.apply)(AdvertiserBusiness.unapply)
  )

  def getAll:Try[Seq[AdvertiserListItem]] = {
    Try(DB.withTransaction { implicit c =>
      SQL("select * from advert.admin_get_advertisers()")
        .apply()
        .map(row =>
          AdvertiserListItem(row[Long]("_advertiser_id"),
            row[String]("_name"), row[String]("_company_name"),
            row[String]("_email"), row[Option[DateTime]]("_active_util"),
            row[String]("_plan_name"), row[Option[BigDecimal]]("_price"),
            row[String]("_status")
          ))
        .toList
    })
  }

  def addGold(addInfo: AdvertiserAddGold):Try[AdvertiserAddGoldConfirm] = {
    Try(DB.withTransaction { implicit c =>
      val randomPassword = Random.alphanumeric.take(8).mkString
      val emailConfirmToken = java.util.UUID.randomUUID.toString

      SQL(s"select * from advert.admin_add_gold_advertisers({name}, {companyName}, {email}, {password}, {price}, {priority}, {photoLimit}, {emailConfirmToken})")
        .on('name -> addInfo.name,
            'companyName -> addInfo.companyName,
            'email -> addInfo.email,
            'password -> Codecs.sha1(randomPassword),
            'price -> addInfo.price,
            'priority -> Plan.gold.getInt("priority").getOrElse(300),
            'photoLimit -> Plan.gold.getInt("photoLimit").getOrElse(10),
            'emailConfirmToken -> emailConfirmToken
        )
        .apply().map(row =>
          AdvertiserAddGoldConfirm(row[Long]("_advertiser_id"),
            addInfo.name, addInfo.email, emailConfirmToken, addInfo.price, randomPassword)
        ).head
    })
  }

  def getBasicInfo(advertiserId: Long):Try[Option[AdvertiserBasicInfo]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.admin_get_advertiser_basic_info($advertiserId)")
        .apply()
        .map(row =>
          AdvertiserBasicInfo(advertiserId,
            row[String]("_name"), row[String]("_company_name"),
            row[String]("_email"), row[String]("_status"),
            row[DateTime]("_created_at"), row[Option[DateTime]]("_active_util"),
            row[Option[String]]("_suspended_reason"),
            row[Option[String]]("_admin_note")
        )).headOption
    })
  }

  def saveBasicInfo(basicInfo: AdvertiserBasicInfo):Try[Unit] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.admin_save_advertiser_basic_info(${basicInfo.advertiserId}, {name}, {companyName}, {email}, {suspendedReason}, {adminNote})")
      .on('name -> basicInfo.name.trim,
          'companyName -> basicInfo.companyName.trim,
          'email -> basicInfo.email.trim.toLowerCase,
          'suspendedReason -> basicInfo.suspendedReason.map(s => s.trim),
          'adminNote -> basicInfo.adminNote.map(s => s.trim)
        )
      .executeQuery()
    })
  }

  def suspend(advertiserId: Long,
              suspendInfo: AdvertiserSuspend):Try[Unit] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.admin_suspend_advertiser($advertiserId, {reason})")
      .on('reason -> suspendInfo.reason)
      .executeQuery()
    })
  }

  def resume(advertiserId: Long):Try[Unit] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.admin_resume_advertiser($advertiserId)")
      .executeQuery()
    })
  }

  def getPlan(advertiserId: Long):Try[Option[AdvertiserPlan]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select name, plan_name, price, photo_limit, balance, balance_charged_at from advert.advertisers where advertiser_id = $advertiserId")
      .apply().map(row =>
        AdvertiserPlan(advertiserId,
          row[String]("name"), row[String]("plan_name"),
          row[BigDecimal]("price"), row[Int]("photo_limit"),
          row[BigDecimal]("balance"), row[Option[DateTime]]("balance_charged_at")
        )
      ).headOption
    })
  }

  def changePlan(planInfo: AdvertiserPlan):Try[Unit] = {
    Try(DB.withTransaction { implicit c =>
      val planConfig = planInfo.planName.toLowerCase match {
        case "bronze" => Plan.bronze
        case "silver" => Plan.silver
        case "gold" => Plan.gold
        case _ => throw new Exception("Invalid advert plan name")
      }

      SQL(s"select * from advert.change_plan(${planInfo.advertiserId}, {planName}, {price}, {photoLimit}, {priority})")
      .on('planName -> planInfo.planName,
          'price -> planInfo.price,
          'photoLimit -> planInfo.photoLimit,
          'priority -> planConfig.getInt("priority").get
        )
      .executeQuery()
    })
  }

  def getBusiness(advertiserId: Long):Try[Option[AdvertiserBusiness]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.admin_get_advertiser_business($advertiserId)")
      .apply()
      .map(row =>
        AdvertiserBusiness(advertiserId, row[Long]("_advert_id"), row[String]("_name"),
          row[String]("_business_name"), row[Option[String]]("_contact_number"),
          row[Option[String]]("_website"), row[String]("_address"),
          row[Double]("_latitude"), row[Double]("_longitude"),
          row[String]("_description"), row[Long]("_displayed_times")
        )
      ).headOption match {
        case Some(businessInfo) => Some(businessInfo)
        case None =>
          SQL(s"select name from advert.advertisers where advertiser_id = $advertiserId")
            .apply()
            .map(row =>
              AdvertiserBusiness(advertiserId, 0, row[String]("name"), "", Some(""), Some(""), "", 0.0, 0.0, "", 0L)
            ).headOption
      }
    })
  }

  def saveBusiness(businessInfo: AdvertiserBusiness):Try[Unit] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.admin_save_advertiser_business(${businessInfo.advertiserId}, ${businessInfo.advertId}, {businessName}, {contactNumber}, {website}, {address}, {latitude}, {longitude}, {description})")
      .on('businessName -> businessInfo.businessName.trim,
          'contactNumber -> businessInfo.contactNumber.map(c => c.trim),
          'website -> businessInfo.website.map(w => w.trim),
          'address -> businessInfo.address.trim,
          'latitude -> businessInfo.latitude,
          'longitude -> businessInfo.longitude,
          'description -> businessInfo.description.trim
        )
      .executeQuery()
    })
  }

  def getAdvertPhotos(advertiserId: Long):Try[Option[AdvertiserAdvertPhotos]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.admin_get_advertiser_advert_photos($advertiserId)")
      .apply().map(row =>
        AdvertiserAdvertPhotos(advertiserId,
          row[String]("_name"), row[Array[String]]("_photos"))
      ).headOption
    })
  }

  def updateAdvertPhotos(advertiserId: Long,
                         index: Int,
                         photo: MultipartFormData.FilePart[Files.TemporaryFile]):Try[String] = {
    Try(DB.withTransaction {implicit c =>
      if (index < 1 || index > 20) throw new Exception("Invalid advert photo index")

      java.nio.file.Files.createDirectories(Paths.get(uploadFolder(advertiserId)))

      val photoName = s"${java.util.UUID.randomUUID.toString}.${FilenameUtils.getExtension(photo.filename)}"

      val photoPath = Paths.get(uploadFolder(advertiserId), photoName).normalize().toString
      photo.ref.moveTo(new File(photoPath), replace = true)

      val photoServerPath = ServerNodeName + "/" + Paths.get(uploadServerPath(advertiserId), photoName).normalize().toString
      SQL(s"select photos[$index] as photo_path from advert.adverts where advertiser_id = $advertiserId")
        .apply().headOption match {
        case Some(row) =>
          deleteAdvertPhotoFromDisk(advertiserId, row[String]("photo_path"))

          SQL(s"update advert.adverts set photos[$index] = '$photoServerPath' where advertiser_id = $advertiserId")
            .executeUpdate()
          photoServerPath
        case None =>
          throw new Exception("Cannot find relevant advertiser's advert photos information")
      }
    })
  }

  def deleteAdvertPhoto(advertiserId: Long,
                        index: Int):Try[Int] = {
    Try(DB.withTransaction { implicit c =>
      if (index < 1 || index > 20) throw new Exception("Invalid advert photo index")

      SQL(s"select photos[$index] as photo_path from advert.adverts where advertiser_id = $advertiserId")
        .apply().headOption match {
        case Some(row) =>
          deleteAdvertPhotoFromDisk(advertiserId, row[String]("photo_path"))
          SQL(s"update advert.adverts set photos[$index] = '' where advertiser_id = $advertiserId").executeUpdate()
        case None =>
          throw new Exception("Cannot find relevant advertiser's advert photos information")
      }
    })
  }

  def reorganiseAdvertPhotos(advertiserId: Long):Try[Unit] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from advert.admin_reorganise_advertiser_advert_photos($advertiserId)").executeQuery()
    })
  }

  def getBills(advertiserId: Long):Try[(Option[String], List[AdvertiserBill])] = {
    Try(DB.withTransaction { implicit c =>
      val name = SQL(s"select name from advert.advertisers where advertiser_id = $advertiserId")
        .apply()
        .map(row => row[String]("name"))
        .headOption

      val bills = SQL(s"select * from advert.admin_get_advertiser_bills($advertiserId)")
        .apply()
        .map(row => AdvertiserBill(row[Long]("_bill_id"),
          row[DateTime]("_issued_at"), row[Option[DateTime]]("_paid_at"),
          row[Option[DateTime]]("_expiring_at"), row[Option[DateTime]]("_canceled_at"),
          row[BigDecimal]("_amount"), row[Option[String]]("_payment_id"),
          row[String]("_status")
        ))
        .toList

      (name, bills)
    })
  }

  private def uploadFolder(userId: Long) = {
    UploadServerRoot + UploadServerFolder + s"advertiser-${userId.toString}"
  }

  private def uploadServerPath(userId: Long) = {
    UploadServerFolder + s"advertiser-${userId.toString}"
  }

  private def deleteAdvertPhotoFromDisk(advertiserId: Long,
                                        path: String) = {
    try {
      if (path.length > 0) {
        val fileName = FilenameUtils.getName(path)
        if (fileName.length > 0) {
          val filePath = Paths.get(uploadFolder(advertiserId), fileName)
          if (!java.nio.file.Files.isDirectory(filePath)) {
            java.nio.file.Files.deleteIfExists(filePath)
          }
        }
      }
    } catch {
      case ex:Exception => Logger.error(s"Fail to delete a advert photo from disk: ${ex.getMessage}")
    }
  }

  private def emailNotExists(email: String):Try[Boolean] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select 1 from advert.advertisers where lower(email) = lower('$email')")().headOption match {
        case Some(_) => false
        case None => true
      }
    })
  }
}
