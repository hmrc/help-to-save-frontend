/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavefrontend.controllers

import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Request, Result}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavefrontend.auth.HelpToSaveAuth
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavefrontend.connectors.SessionCacheConnector
import uk.gov.hmrc.helptosavefrontend.forms.{BankDetails, BankDetailsValidation}
import uk.gov.hmrc.helptosavefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavefrontend.models.eligibility.IneligibilityReason
import uk.gov.hmrc.helptosavefrontend.models.{HTSSession, HtsContextWithNINO}
import uk.gov.hmrc.helptosavefrontend.services.{BarsService, HelpToSaveService}
import uk.gov.hmrc.helptosavefrontend.util.Logging._
import uk.gov.hmrc.helptosavefrontend.util._
import uk.gov.hmrc.helptosavefrontend.views

import scala.concurrent.Future

@Singleton
class BankAccountController @Inject() (val helpToSaveService:     HelpToSaveService,
                                       val sessionCacheConnector: SessionCacheConnector,
                                       val authConnector:         AuthConnector,
                                       val metrics:               Metrics,
                                       val barsService:           BarsService)(implicit override val messagesApi: MessagesApi,
                                                                               val transformer:       NINOLogMessageTransformer,
                                                                               val frontendAppConfig: FrontendAppConfig,
                                                                               val config:            Configuration,
                                                                               val env:               Environment,
                                                                               bankDetailsValidation: BankDetailsValidation)

  extends BaseController with HelpToSaveAuth with EnrolmentCheckBehaviour with SessionBehaviour {

  private val selectEmailPage = routes.EmailController.getSelectEmailPage().url

  private def backLinkFromSession(session: HTSSession): String =
    if (session.changingDetails) { routes.RegisterController.getCreateAccountPage().url } else { selectEmailPage }

  def getBankDetailsPage(): Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolledAndDoneEligibilityChecks(htsContext.nino) {
      s ⇒
        s.bankDetails.fold(
          Ok(views.html.register.bank_account_details(BankDetails.giveBankDetailsForm(), backLinkFromSession(s)))
        )(bankDetails ⇒
            Ok(views.html.register.bank_account_details(BankDetails.giveBankDetailsForm().fill(bankDetails), backLinkFromSession(s)))
          )
    }
  }(redirectOnLoginURL = routes.BankAccountController.getBankDetailsPage().url)

  def submitBankDetails(): Action[AnyContent] = authorisedForHtsWithNINO { implicit request ⇒ implicit htsContext ⇒
    checkIfAlreadyEnrolledAndDoneEligibilityChecks(htsContext.nino) {
      session ⇒
        BankDetails.giveBankDetailsForm().bindFromRequest().fold(
          withErrors ⇒
            Ok(views.html.register.bank_account_details(withErrors, backLinkFromSession(session))),
          { bankDetails ⇒
            barsService.validate(htsContext.nino, bankDetails, routes.BankAccountController.submitBankDetails().url).map[Future[Result]] {
              case Right(true) ⇒
                sessionCacheConnector.put(session.copy(bankDetails = Some(bankDetails)))
                  .fold(
                    error ⇒ {
                      logger.warn(s"Could not update session with bank details: $error")
                      internalServerError()
                    },
                    _ ⇒ SeeOther(routes.RegisterController.getCreateAccountPage().url)
                  )

              case Right(false) ⇒
                val formWithErrors = BankDetails.giveBankDetailsForm().fill(bankDetails)
                  .withError("sortCode", BankDetailsValidation.ErrorMessages.sortCodeBarsInvalid)
                  .withError("accountNumber", BankDetailsValidation.ErrorMessages.accountNumberBarsInvalid)

                toFuture(Ok(views.html.register.bank_account_details(formWithErrors, backLinkFromSession(session))))

              case Left(e) ⇒
                logger.warn(s"Could not validate bank details with BARS service: $e")
                toFuture(internalServerError())

            }.flatMap(identity)
          }
        )
    }

  }(redirectOnLoginURL = routes.BankAccountController.submitBankDetails().url)

  private def checkIfAlreadyEnrolledAndDoneEligibilityChecks(nino: String)(ifNotEnrolled: HTSSession ⇒ Future[Result])(implicit htsContext: HtsContextWithNINO, request: Request[_]) =
    checkIfAlreadyEnrolled { () ⇒
      checkSession(
        SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
      ) { session ⇒
          session.eligibilityCheckResult.fold[Future[Result]](
            SeeOther(routes.EligibilityCheckController.getCheckEligibility().url)
          )(_.fold[Future[Result]](
              { ineligibleReason ⇒
                val ineligibilityType = IneligibilityReason.fromIneligible(ineligibleReason)

                ineligibilityType.fold {
                  logger.warn(s"Could not parse ineligibility reason when storing bank details: $ineligibleReason", nino)
                  toFuture(internalServerError())
                } { i ⇒
                  toFuture(Ok(views.html.register.not_eligible(i)))
                }
              },
              _ ⇒ ifNotEnrolled(session)
            )
            )
        }
    }
}
