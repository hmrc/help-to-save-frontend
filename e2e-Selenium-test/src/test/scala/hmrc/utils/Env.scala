package hmrc.utils


object Env {

  val baseUrl = Option(System.getProperty("environment")) match {
    case Some("dev") => Urls.DEV
    case Some("qa") => Urls.QA
    case Some("staging") => Urls.STAGING
    case Some("local") => Urls.LOCAL
    case _ => Urls.LOCAL
  }

  val baseAuthWizUrl = Option(System.getProperty("environment")) match {
    case Some("dev") => Urls.DEV
    case Some("qa") => Urls.QA
    case Some("staging") => Urls.STAGING
    case Some("local") => Urls.AUTH_WIZ_LOCAL
    case _ => Urls.AUTH_WIZ_LOCAL
  }

  def isQA (url: String): Boolean = url.startsWith(Urls.QA)
  def isDev(url: String): Boolean = url.startsWith(Urls.DEV)
  def isLocal(url: String): Boolean = url.startsWith(Urls.LOCAL)
  def isStaging(url: String): Boolean = url.startsWith(Urls.STAGING)
}
