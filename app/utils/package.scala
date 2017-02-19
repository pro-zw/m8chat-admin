import anorm.{TypeDoesNotMatch, Column}
import org.apache.commons.mail.{DefaultAuthenticator, Email}
import org.joda.time.DateTime
import play.api.data.FormError
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.mvc.Results._
import play.api.{Logger, Play}
import play.api.Play.current
import play.api.data.validation.ValidationError
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc.{Result, Results}
import scala.language.postfixOps
import scala.util.Try

package object utils {
  object EmailUtil {
    val smtpHost = Play.application.configuration.getString("smtp.host").getOrElse("mail.m8chat.com")
    val smtpPort = Play.application.configuration.getInt("smtp.port").getOrElse(587)
    val smtpUser = Play.application.configuration.getString("smtp.user").getOrElse("webmaster@m8chat.com")
    val smtpPassword = Play.application.configuration.getString("smtp.password").getOrElse("64PscSUvTHRV8wB3")
    val smtpStartTls = Play.application.configuration.getBoolean("smtp.startTls").getOrElse(true)
    val smtpDebug = Play.application.configuration.getBoolean("smtp.debug").getOrElse(false)

    val noReplySender = "noreply@m8chat.com"

    def send(mail: Email) = {
      mail.setHostName(EmailUtil.smtpHost)
      mail.setSmtpPort(EmailUtil.smtpPort)
      mail.setAuthenticator(new DefaultAuthenticator(EmailUtil.smtpUser, EmailUtil.smtpPassword))
      mail.setSSLCheckServerIdentity(false)
      mail.setStartTLSRequired(EmailUtil.smtpStartTls)
      mail.setDebug(EmailUtil.smtpDebug)
      mail.send()
    }
  }

  // Json error message writes
  implicit val JsPathWrites =
    Writes[JsPath](p => JsString(p.toString()))

  implicit val ValidationErrorWrites =
    Writes[ValidationError](e => JsString(e.message))

  implicit val JsonValidateErrorWrites = (
    (__ \ "path").write[JsPath] and
      (__ \ "messages").write[Seq[ValidationError]]
      tupled
    )

  implicit val JsonThrowableWrites = new Writes[Throwable] {
    def writes(throwable: Throwable) = Json.obj(
      "path" -> throwable.getClass.getName,
      "messages" -> Json.arr(throwable.getMessage)
    )
  }

  // Json error results
  def JsonErrorResult(result: Results.Status, message: String) = {
    Logger.info(s"JsonErrorResult ($result): $message")
    result(Json.obj(
      "errors" -> Json.arr(Json.toJson(new Exception(message)))
    ))
  }

  def JsonBadRequest(message: String) = JsonErrorResult(Results.BadRequest, message)
  def JsonBadRequest(errors: Seq[(JsPath, Seq[ValidationError])]) = {
    BadRequest(Json.obj("errors" -> Json.toJson(errors)))
  }
  def JsonBadRequest(ex: Throwable) = {
    BadRequest(Json.obj("errors" -> Json.arr(Json.toJson(ex))))
  }

  // Upload
  val UploadServerRoot = Play.application.configuration.getString("upload.server.root").get
  val UploadServerFolder = Play.application.configuration.getString("upload.server.folder").get

  val ServerNodeName = Play.application.configuration.getString("node.name").getOrElse("m8chat.com")

  val RootUrl = "https://" + Play.application.configuration.getString("node.frontend.name").getOrElse("m8chat.com")

  val EmailMask = """([A-Z0-9a-z.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9]+)+)""".r

  // Handle anorm array column
  implicit def rowToStringArray: Column[Array[String]] = Column.nonNull { (value, meta) =>
    // val MetaDataItem(qualified, nullable, clazz) = meta
    value match {
      case o: java.sql.Array => Right(o.getArray.asInstanceOf[Array[String]])
      case _ => Left(TypeDoesNotMatch("Cannot convert " + value + ":" + value.asInstanceOf[AnyRef].getClass))
    }
  }

  // JsResult class improvements
  implicit class JsResultImprovements[+A](jsResult: JsResult[A]) {
    def toResponse = jsResult.fold(errors => JsonBadRequest(errors), _:(A => Result))
  }

  // Custom form mapping
  val moneyMapping = new Formatter[BigDecimal] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], BigDecimal] = {
      data.get(key).map { value =>
        Try {
          Right(BigDecimal(value))
        } getOrElse Left(Seq(FormError(key, "error.number")))
      } getOrElse Left(Seq(FormError(key, "error.required")))
    }

    override def unbind(key: String, value: BigDecimal): Map[String, String] = {
      Map(key -> f"$value%.2f")
    }

    override val format = Some(("money.format", Nil))
  }

  val readonlyDateTimeMapping = new Formatter[DateTime] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], DateTime] = {
      // We won't use this value
      Right(DateTime.now())
    }

    override def unbind(key: String, value: DateTime): Map[String, String] = {
      Map(key -> value.getMillis.toString)
    }

    override val format = Some(("readonly.datetime.format", Nil))
  }
}
