package rnrb.utils

case class Configuration (rnrbUrl: String, timeout: Int)

object Configuration {

  val basePath = "help-to-save"

  lazy val host = System.getProperty("host", "https://www-dev.tax.service.gov.uk")

  lazy val timeout = System.getProperty("timeout", "5")

  lazy val settings: Configuration = create()

  private def create(): Configuration = {
    new Configuration(s"$host/$basePath", timeout.toInt)
  }
}
