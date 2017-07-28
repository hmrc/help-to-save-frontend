package uk.gov.hmrc.helptosavefrontend.util

import play.api.Logger

trait Logging {

  val logger = Logger(classOf[this])

}
