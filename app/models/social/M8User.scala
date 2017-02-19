package models.social

import java.io.File
import java.nio.file.Paths
import java.util.Locale

import anorm._
import org.apache.commons.io.FilenameUtils
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.Logger
import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats
import play.api.db.DB
import play.api.Play.current
import play.api.libs.Files
import play.api.mvc.MultipartFormData
import scala.util.Try
import utils._

case class M8UserListItem(userId: Long,
                          username: String,
                          firstName: String,
                          email: String,
                          gender: String,
                          createdAt: DateTime,
                          blockedTimes: Int)

case class M8UserBasicInfo(userId: Long,
                           email: String,
                           username: String,
                           firstName: String,
                           gender: String,
                           preferGender: String,
                           description: String,
                           createdAt: DateTime,
                           latitude: Option[Double],
                           longitude: Option[Double],
                           interests: Option[String],
                           blockedTimes: Int,
                           adminNote: Option[String])

case class M8UserPictures(userId: Long,
                          username: String,
                          pictures: Seq[String])

object M8User {
  val validGenders = List("Male", "Female")
  val validPrefGenders = List("Male", "Female", "Both")

  val basicInfoForm = Form(
    mapping(
      "userId" -> longNumber(min = 1L),
      "email" -> nonEmptyText(maxLength = 180)
        .verifying("Invalid email address",
          email => email match {
            case EmailMask(_) => true
            case _ => false
          }),
      "username" -> nonEmptyText(minLength = 3, maxLength = 14),
      "firstName" -> nonEmptyText(maxLength = 40),
      "gender" -> nonEmptyText()
        .verifying("Invalid gender value",
          gender => { validGenders.contains(gender) }
        ),
      "preferGender" -> nonEmptyText()
        .verifying("Invalid prefer gender value",
          preferGender => { validPrefGenders.contains(preferGender) }
        ),
      "description" -> nonEmptyText(maxLength = 500),
      "createdAt" -> of(readonlyDateTimeMapping),
      "latitude" -> optional(of(Formats.doubleFormat)
        .verifying("Invalid latitude value",
          latitude => { latitude >= -90.0 && latitude <= 90.0 }
        )),
      "longitude" -> optional(of(Formats.doubleFormat)
        .verifying("Invalid longitude value",
          longitude => { longitude >= -180.0 && longitude <= 180.0 }
        )),
      "interests" -> optional(text),
      "blockedTimes" -> number(min = 0),
      "adminNote" -> optional(text)
    )(M8UserBasicInfo.apply)(M8UserBasicInfo.unapply)
  )

  def getAll:Try[Seq[M8UserListItem]] = {
    Try(DB.withTransaction { implicit c =>
      SQL("select * from social.admin_get_users()")
        .apply()
        .map(row =>
          M8UserListItem(row[Long]("_user_id"),
            row[String]("_username"), row[String]("_first_name"),
            row[String]("_email"), row[String]("_gender"),
            row[DateTime]("_create_at"), row[Int]("_blocked_times")
        ))
        .toList
    })
  }

  def getBasicInfo(userId: Long):Try[Option[M8UserBasicInfo]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from social.admin_get_user_basic_info($userId)")
        .apply()
        .map(row =>
          M8UserBasicInfo(userId,
            row[String]("_email"), row[String]("_username"),
            row[String]("_first_name"), row[String]("_gender"),
            row[String]("_prefer_gender"), row[String]("_description"),
            row[DateTime]("_created_at"), row[Option[Double]]("_latitude"),
            row[Option[Double]]("_longitude"), row[Option[String]]("_interests"),
            row[Int]("_blocked_times"), row[Option[String]]("_admin_note")
        )).headOption
    })
  }

  def saveBasicInfo(basicInfo: M8UserBasicInfo):Try[Unit] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from social.admin_save_user_basic_info(${basicInfo.userId}, {email}, {username}, {firstName}, {gender}, {preferGender}, {description}, {latitude}, {longitude}, ${basicInfo.blockedTimes}, {adminNote})")
      .on('email -> basicInfo.email.trim.toLowerCase,
          'username -> basicInfo.username.trim,
          'firstName -> basicInfo.firstName.trim,
          'gender -> basicInfo.gender,
          'preferGender -> basicInfo.preferGender,
          'description -> basicInfo.description.trim,
          'latitude -> basicInfo.latitude,
          'longitude -> basicInfo.longitude,
          'adminNote -> basicInfo.adminNote.map(s => s.trim)
        )
      .executeQuery()
    })
  }

  def getPictures(userId: Long):Try[Option[M8UserPictures]] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select username, pictures[1:6] as _pictures from social.m8_users where user_id = $userId")
        .apply()
        .map(row => M8UserPictures(userId, row[String]("username"), row[Array[String]]("_pictures")))
        .headOption
    })
  }

  def updatePicture(userId: Long,
                    index: Int,
                    picture: MultipartFormData.FilePart[Files.TemporaryFile]):Try[String] = {
    Try(DB.withTransaction { implicit c =>
      if (index < 1 || index > 6) throw new Exception("Invalid picture index")

      java.nio.file.Files.createDirectories(Paths.get(uploadFolder(userId)))

      val pictureName = s"${java.util.UUID.randomUUID.toString}.${FilenameUtils.getExtension(picture.filename)}"

      val picturePath = Paths.get(uploadFolder(userId), pictureName).normalize().toString
      picture.ref.moveTo(new File(picturePath), replace = true)

      val pictureServerPath = ServerNodeName + "/" + Paths.get(uploadServerPath(userId), pictureName).normalize().toString
      SQL(s"select pictures[$index] as picture_path from social.m8_users where user_id = $userId")
        .apply().headOption match {
        case Some(row) =>
          deletePictureFromDisk(userId, row[String]("picture_path"))

          SQL(s"update social.m8_users set pictures[$index] = '$pictureServerPath' where user_id = $userId")
            .executeUpdate()
          pictureServerPath
        case None =>
          throw new Exception("Cannot find relevant mobile user's information")
      }
    })
  }

  def deletePicture(userId: Long,
                    index: Int):Try[Int] = {
    Try(DB.withTransaction { implicit c =>
      if (index < 1 || index > 6) throw new Exception("Invalid picture index")

      SQL(s"select pictures[$index] as picture_path from social.m8_users where user_id = $userId")
        .apply().headOption match {
        case Some(row) =>
          deletePictureFromDisk(userId, row[String]("picture_path"))
          SQL(s"update social.m8_users set pictures[$index] = '' where user_id = $userId").executeUpdate()
        case None =>
          throw new Exception("Cannot find relevant mobile user's information")
      }
    })
  }

  def reorganisePictures(userId: Long):Try[Unit] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select * from social.admin_reorganise_user_pictures($userId)").executeQuery()
    })
  }

  private def uploadFolder(userId: Long) = {
    UploadServerRoot + UploadServerFolder + s"m8user-${userId.toString}"
  }

  private def uploadServerPath(userId: Long) = {
    UploadServerFolder + s"m8user-${userId.toString}"
  }

  private def deletePictureFromDisk(userId: Long,
                                    path: String) = {
    try {
      if (path.length > 0) {
        val fileName = FilenameUtils.getName(path)
        if (fileName.length > 0) {
          val filePath = Paths.get(uploadFolder(userId), fileName)
          if (!java.nio.file.Files.isDirectory(filePath)) {
            java.nio.file.Files.deleteIfExists(filePath)
          }
        }
      }
    } catch {
      case ex:Exception => Logger.error(s"Fail to delete a picture from disk: ${ex.getMessage}")
    }
  }

  /*
  private def emailNotExists(email: String):Try[Boolean] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select 1 from social.m8_users where lower(email) = lower('$email')")().headOption match {
        case Some(_) => false
        case None => true
      }
    })
  }

  private def usernameNotExists(username: String):Try[Boolean] = {
    Try(DB.withTransaction { implicit c =>
      SQL(s"select 1 from social.m8_users where lower(username) = lower('$username')")().headOption match {
        case Some(_) => false
        case None => true
      }
    })
  }
  */
}
