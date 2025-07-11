/*
 * Copyright 2023 HM Revenue & Customs
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
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json._
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.connectors.HelpToSaveConnectorImpl._
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.models.account.{Account, AccountNumber}
import uk.gov.hmrc.helptosavefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResultType}
import uk.gov.hmrc.helptosavefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavefrontend.models.userinfo.{MissingUserInfo, NSIPayload}
import uk.gov.hmrc.helptosavefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavefrontend.util.{Email, Result, base64Encode, maskNino}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def getEligibility()(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, String, EligibilityCheckResultType]

  def getUserEnrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EnrolmentStatus]

  def setITMPFlagAndUpdateMongo()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def storeEmail(email: Email)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def getEmail()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[String]]

  def isAccountCreationAllowed()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserCapResponse]

  def getAccount(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Account]

  def createAccount(
    createAccountRequest: CreateAccountRequest
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[HttpResponse]

  def updateEmail(userInfo: NSIPayload)(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[HttpResponse]

  def validateBankDetails(
    request: ValidateBankDetailsRequest
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[HttpResponse]

  def getAccountNumber()(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[AccountNumber]

}

@Singleton
class HelpToSaveConnectorImpl @Inject() (http: HttpClientV2)(implicit frontendAppConfig: FrontendAppConfig)
    extends HelpToSaveConnector {

  private val helpToSaveUrl: String = frontendAppConfig.helpToSaveUrl

  private val eligibilityURL =
    s"$helpToSaveUrl/help-to-save/eligibility-check"

  private val enrolmentStatusURL =
    s"$helpToSaveUrl/help-to-save/enrolment-status"

  private val setITMPFlagURL =
    s"$helpToSaveUrl/help-to-save/set-itmp-flag"

  private val storeEmailURL =
    s"$helpToSaveUrl/help-to-save/store-email"

  private val getEmailURL =
    s"$helpToSaveUrl/help-to-save/get-email"

  private val accountCreateAllowedURL =
    s"$helpToSaveUrl/help-to-save/account-create-allowed"

  private val createAccountURL =
    s"$helpToSaveUrl/help-to-save/create-account"

  private val updateEmailURL =
    s"$helpToSaveUrl/help-to-save/update-email"

  private val validateBankDetailsURL =
    s"$helpToSaveUrl/help-to-save/validate-bank-details"

  private val getAccountNumberURL =
    s"$helpToSaveUrl/help-to-save/get-account-number"

  private def getAccountUrl(nino: String) = s"$helpToSaveUrl/help-to-save/$nino/account"

  private def getAccountQueryParams(correlationId: UUID): Seq[(String, String)] =
    Seq("correlationId" -> correlationId.toString, "systemId" -> "help-to-save-frontend")

  private val emptyQueryParameters: Seq[(String, String)] = Seq.empty

  def getEligibility()(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): EitherT[Future, String, EligibilityCheckResultType] =
    handleGet(
      eligibilityURL,
      emptyQueryParameters,
      _.parseJSON[EligibilityCheckResponse]().flatMap(toEligibilityCheckResult),
      "check eligibility",
      identity
    )

  def getUserEnrolmentStatus()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EnrolmentStatus] =
    handleGet(
      enrolmentStatusURL,
      emptyQueryParameters,
      _.parseJSON[EnrolmentStatus](),
      "get user enrolment status",
      identity
    )
  def setITMPFlagAndUpdateMongo()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    handleGet(setITMPFlagURL, emptyQueryParameters, _ => Right(()), "set ITMP flag and update mongo", identity)

  def storeEmail(email: Email)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    handleGet(storeEmailURL, Seq("email" -> new String(base64Encode(email))), _ => Right(()), "store email", identity)

  def getEmail()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Option[String]] =
    handleGet(getEmailURL, emptyQueryParameters, _.parseJSON[GetEmailResponse]().map(_.email), "get email", identity)

  def isAccountCreationAllowed()(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[UserCapResponse] =
    handleGet(
      accountCreateAllowedURL,
      emptyQueryParameters,
      _.parseJSON[UserCapResponse](),
      "account creation allowed",
      identity
    )

  def getAccount(nino: String, correlationId: UUID)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Account] =
    handleGet(
      getAccountUrl(nino),
      getAccountQueryParams(correlationId),
      _.parseJSON[Account](),
      "get Account",
      identity
    )

  def getAccountNumber()(implicit hc: HeaderCarrier, ex: ExecutionContext): Result[AccountNumber] =
    handleGet(getAccountNumberURL, emptyQueryParameters, _.parseJSON[AccountNumber](), "get account number", identity)

  private def handleGet[A, B](
    url: String,
    queryParameters: Seq[(String, String)],
    ifHTTP200: HttpResponse => Either[B, A],
    description: => String,
    toError: String => B
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, B, A] =
    handle(
      http
        .get(url"$url")
        .transform(_.withQueryStringParameters(queryParameters: _*))
        .execute[HttpResponse],
      ifHTTP200,
      description,
      toError
    )

  private def handle[A, B](
    resF: Future[HttpResponse],
    ifHTTP200: HttpResponse => Either[B, A],
    description: => String,
    toError: String => B
  )(implicit ec: ExecutionContext) =
    EitherT(
      resF
        .map { response =>
          if (response.status == 200) {
            ifHTTP200(response)
          } else {
            Left(
              toError(
                s"Call to $description came back with status ${response.status}. Body was ${maskNino(response.body)}"
              )
            )
          }
        }
        .recover {
          case NonFatal(t) => Left(toError(s"Call to $description failed: ${t.getMessage}"))
        }
    )

  // scalastyle:off magic.number
  private def toEligibilityCheckResult(response: EligibilityCheckResponse): Either[String, EligibilityCheckResultType] =
    response.eligibilityCheckResult.resultCode match {
      case 1     => Right(EligibilityCheckResultType.Eligible(response))
      case 2 | 4 => Right(EligibilityCheckResultType.Ineligible(response))
      case 3     => Right(EligibilityCheckResultType.AlreadyHasAccount(response))
      case other => Left(s"Could not parse eligibility result code '$other'. Response was '$response'")
    }

  override def createAccount(
    createAccountRequest: CreateAccountRequest
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.post(url"$createAccountURL").withBody(Json.toJson(createAccountRequest)).execute[HttpResponse]

  override def updateEmail(
    userInfo: NSIPayload
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] =
    http.put(url"$updateEmailURL").withBody(Json.toJson(userInfo)).execute[HttpResponse]

  override def validateBankDetails(
    request: ValidateBankDetailsRequest
  )(implicit hc: HeaderCarrier, ex: ExecutionContext): Future[HttpResponse] =
    http.post(url"$validateBankDetailsURL").withBody(Json.toJson(request)).execute[HttpResponse]
}

object HelpToSaveConnectorImpl {

  private[connectors] case class MissingUserInfoSet(missingInfo: Set[MissingUserInfo])

  private[connectors] case class GetEmailResponse(email: Option[String])

  private[connectors] object GetEmailResponse {
    implicit val format: Format[GetEmailResponse] = Json.format[GetEmailResponse]
  }

}
