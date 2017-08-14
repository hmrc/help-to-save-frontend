/*
 * Copyright 2017 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.helptosavefrontend.connectors

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig._
import uk.gov.hmrc.helptosavefrontend.config.{WSHttp, WSHttpExtension}
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl.{EligibilityCheckResponse, MissingUserInfoSet}
import uk.gov.hmrc.helptosavefrontend.models.UserInformationRetrievalError.MissingUserInfos
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.{NINO, UserDetailsURI}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def getEligibility(nino: NINO)(implicit hc: HeaderCarrier): EitherT[Future,String,EligibilityCheckResult]

  def getUserInformation(nino: NINO,
                         userDetailsURI: UserDetailsURI)
                        (implicit hc: HeaderCarrier): EitherT[Future,UserInformationRetrievalError,UserInfo]

}

@Singleton
class HelpToSaveConnectorImpl @Inject()(implicit ec: ExecutionContext) extends HelpToSaveConnector {

  val http: WSHttpExtension = WSHttp

  override def getEligibility(nino: NINO)(implicit hc: HeaderCarrier): EitherT[Future,String,EligibilityCheckResult] = {
    EitherT.right[Future, String, HttpResponse](http.get(eligibilityURL(nino)))
      .subflatMap { response ⇒
        if (response.status == 200) {
          response.parseJson[EligibilityCheckResponse].flatMap(r ⇒ toEligibilityCheckResponse(r))
        } else {
          Left(s"Call to check eligibility came back with status ${response.status}")
        }
      }

  }

  def getUserInformation(nino: NINO, userDetailsURI: UserDetailsURI
                        )(implicit hc: HeaderCarrier): EitherT[Future,UserInformationRetrievalError,UserInfo] = {
    val backendError = (s: String) ⇒ UserInformationRetrievalError.BackendError(s, nino)
    EitherT.right[Future, String, HttpResponse](http.get(userInformationURL(nino, userDetailsURI)))
      .leftMap(backendError)
      .subflatMap { response ⇒
        if(response.status == 200) {
          response.parseJson[UserInfo].fold[Either[UserInformationRetrievalError, UserInfo]](
            // couldn't parse user info in this case - try to parse as missing user info
            _ ⇒
              response.parseJson[MissingUserInfoSet].fold(
                _ ⇒ Left(backendError("Could not parse JSON reponse from user information endpoint")),
                m ⇒ Left(MissingUserInfos(m.missingInfo, nino))
              ),
            Right(_)
          )
        } else {
          Left(backendError(s"Call to get user details came back with status ${response.status}"))
        }
      }
  }

  private def eligibilityURL(nino: NINO) =
    s"$helpToSaveUrl/help-to-save/eligibility-check?nino=$nino"

  private def userInformationURL(nino: NINO, userDetailsURI: String) =
    s"$helpToSaveUrl/help-to-save/user-information?nino=$nino&userDetailsURI=${encoded(userDetailsURI)}"

  private def toEligibilityCheckResponse(eligibilityCheckResponse: EligibilityCheckResponse): Either[String,EligibilityCheckResult] = {
    val reasonInt = eligibilityCheckResponse.reason

    eligibilityCheckResponse.result match {
      case 1     ⇒
        // user is eligible
        Either.fromOption(
          EligibilityReason.fromInt(reasonInt).map(r ⇒ EligibilityCheckResult(Right(r))),
          s"Could not parse ineligibility reason '$reasonInt'")

      case 2     ⇒
        // user is ineligible
        Either.fromOption(
          IneligibilityReason.fromInt(reasonInt).map(r ⇒ EligibilityCheckResult(Left(r))),
          s"Could not parse eligibility reason '$reasonInt'")

      case other ⇒
        Left(s"Could not parse eligibility result '$other'")

    }
  }

}


object HelpToSaveConnectorImpl {

  private[connectors] case class MissingUserInfoSet(missingInfo: Set[MissingUserInfo])

  private[connectors] object MissingUserInfoSet {
    implicit val missingUserInfoSetFormat: Format[MissingUserInfoSet] =
      Json.format[MissingUserInfoSet]
  }

  private[connectors] case class EligibilityCheckResponse(result: Int, reason: Int)

  private[connectors] object EligibilityCheckResponse {

    implicit val format: Format[EligibilityCheckResponse] = Json.format[EligibilityCheckResponse]

  }

}