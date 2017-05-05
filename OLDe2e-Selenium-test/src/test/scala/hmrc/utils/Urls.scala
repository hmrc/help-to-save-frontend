package hmrc.utils

object Urls extends Enumeration {
  val LOCAL = "http://localhost:9895"
  val DEV = "https://www-dev.tax.service.gov.uk"
  val QA = "https://www-qa.tax.service.gov.uk"
  val STAGING = "https://www-staging.tax.service.gov.uk"
  val AUTH_WIZ_LOCAL = "http://localhost:9949"
}
