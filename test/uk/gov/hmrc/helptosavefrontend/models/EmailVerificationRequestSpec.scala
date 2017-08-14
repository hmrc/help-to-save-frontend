package uk.gov.hmrc.helptosavefrontend.models

import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.play.test.UnitSpec

class EmailVerificationRequestSpec extends UnitSpec with TestSupport {


"the constructor" should {

  "check that the nino and the new email address have been passed in as template parameters" in {
    val email = "aemail@gmail.com"
    val nino = "AE1234XXX"
    val emailVerificationRequest = EmailVerificationRequest(email, nino, "templateID", "P1D", "http;//continue.url.com", Map())
    emailVerificationRequest.templateParameters.getOrElse("email", "") shouldBe email
    emailVerificationRequest.templateParameters.getOrElse("nino", "") shouldBe nino
  }
  }
}
