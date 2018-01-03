package uk.gov.hmrc.helptosavefrontend.connectors

import com.google.inject.Inject
import play.api.libs.json.Json
import uk.gov.hmrc.helptosavefrontend.config.WSHttp
import uk.gov.hmrc.helptosavefrontend.connectors.AuthConnector.AuthToken
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AuthConnector @Inject() (http: WSHttp) {

  private val authLoginApiUrl = "http://localhost:8585/government-gateway/legacy/login"

  def login(body: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, AuthToken]] = {
    http.post(authLoginApiUrl, Json.parse(body)).map {
      response ⇒
        response.header("authorization") match {
          case Some(token) ⇒ Right(token)
          case None        ⇒ Left(s"error during login, response from auth = ${response.body}")
        }
    }.recover {
      case ex ⇒ Left(s"error during auth, error=${ex.getMessage}")
    }
  }

}

object AuthConnector {

  type AuthToken = String

}
