package uk.gov.hmrc.helptosavefrontend

import org.scalatest.Suite
import uk.gov.hmrc.helptosavefrontend.connectors.AuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.logging.Authorization

trait AuthorisationSupport { this: IntegrationTest with Suite ⇒

  lazy val authConnector: AuthConnector = application.injector.instanceOf[AuthConnector]

  def authorisedTest(nino: String, test: HeaderCarrier ⇒ Unit): Unit =
    await(authConnector.login(loginRequestBody(nino))(HeaderCarrier(), ec)).fold(
      e ⇒ fail(e),
      token ⇒ test(HeaderCarrier(authorization = Some(Authorization(token))))
    )

  private def loginRequestBody(nino: String): String =
    s"""{
         "credId":"hts-cred-id",
         "affinityGroup":"Individual",
         "confidenceLevel":200,
         "credentialStrength":"strong",
         "nino":"$nino",
         "enrolments":[
            {
               "key":"IR-SA",
               "identifiers":[
                  {
                     "key":"UTR",
                     "value":"123456789"
                  }
               ],
               "state":"Activated"
            }
         ]
      }"""
}
