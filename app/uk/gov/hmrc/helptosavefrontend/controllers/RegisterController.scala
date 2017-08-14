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

package uk.gov.hmrc.helptosavefrontend.controllers

import javax.inject.Singleton

import cats.instances.future._
import com.google.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{Action, AnyContent, Result}
import play.api.Application
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.SubmissionFailure
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.repo.EmailStore
import uk.gov.hmrc.helptosavefrontend.services.{EnrolmentService, HelpToSaveService}
import uk.gov.hmrc.helptosavefrontend.util.{DataEncrypter, Logging, toFuture}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterController @Inject()(val messagesApi: MessagesApi,
                                   helpToSaveService: HelpToSaveService,
                                   val sessionCacheConnector: SessionCacheConnector,
                                   val enrolmentService: EnrolmentService,
                                   emailStore: EmailStore,
                                   val app: Application)(implicit ec: ExecutionContext)
  extends HelpToSaveAuth(app) with EnrolmentCheckBehaviour with SessionBehaviour with I18nSupport with Logging {

  import RegisterController.NSIUserInfoOps

  def getConfirmDetailsPage: Action[AnyContent] = authorisedForHtsWithInfo {
    implicit request ⇒
      implicit htsContext ⇒
        checkIfAlreadyEnrolled { _ ⇒
          checkIfDoneEligibilityChecks { case (nsiUserInfo, _) ⇒
            Ok(views.html.register.confirm_details(nsiUserInfo))
          }
        }
  }

  def getCreateAccountHelpToSavePage(confirmedEmail: String): Action[AnyContent] = authorisedForHtsWithInfo {
    implicit request ⇒
      implicit htsContext ⇒
        checkIfAlreadyEnrolled { nino ⇒
          checkIfDoneEligibilityChecks { case (nsiUserInfo, _) ⇒
            DataEncrypter.decrypt(confirmedEmail).fold(
              { e ⇒
                logger.warn(s"Could not decrypt email: $e")
                InternalServerError
              },{ email ⇒
                val result = for{
                  _ ← sessionCacheConnector.put(HTSSession(Some(nsiUserInfo), Some(email)))
                  _ ← emailStore.storeConfirmedEmail(email, nino)
                } yield ()

                result.fold(
                  { e ⇒
                    logger.warn(s"Could not store confirmed email: $e")
                    InternalServerError
                  },
                  _ ⇒ Ok(views.html.register.create_account_help_to_save())
                )
              }
            )
          }
        }
  }

  def createAccountHelpToSave: Action[AnyContent] = authorisedForHtsWithInfo {
    implicit request ⇒
      implicit htsContext ⇒
        checkIfAlreadyEnrolled { nino ⇒
          checkIfDoneEligibilityChecks { case (nsiUserInfo, confirmedEmail) ⇒
            confirmedEmail.fold[Future[Result]](
              SeeOther(routes.RegisterController.getConfirmDetailsPage().url)
            ) { email ⇒
              // TODO: plug in actual pages below
              helpToSaveService.createAccount(nsiUserInfo.updateEmail(email)).leftMap(submissionFailureToString).fold(
                error ⇒ {
                  // TODO: error or warning?
                  logger.error(s"Could not create account: $error")
                  Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page(s"Account creation failed: $error"))
                },
                _ ⇒ {
                  logger.info(s"Successfully created account for $nino")
                  // start the process to enrol the user but don't worry about the result
                  enrolmentService.enrolUser(nino).fold(
                    e ⇒ logger.warn(s"Could not start process to enrol user $nino: $e"),
                    _ ⇒ logger.info(s"Started process to enrol user $nino")
                  )

                  Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page("Successfully created account"))
                }
              )
            }
          }
        }
  }

  /**
    * Checks the HTSSession data from keystore - if the is no session the user has not done the eligibility
    * checks yet this session and they are redirected to the 'apply now' page. If the session data indicates
    * that they are not eligible show the user the 'you are not eligible page'. Otherwise, perform the
    * given action if the the session data indicates that they are eligible
    */
  private def checkIfDoneEligibilityChecks(ifEligible: (NSIUserInfo, Option[String]) ⇒ Future[Result]
                                          )(implicit htsContext: HtsContext, hc: HeaderCarrier): Future[Result] =
    checkSession(
      // no session data => user has not gone through the journey this session => take them to eligibility checks
      SeeOther(routes.EligibilityCheckController.getCheckEligibility().url),
      session ⇒
        session.eligibilityCheckResult.fold[Future[Result]](
          // user has gone through journey already this sessions and were found to be ineligible
          SeeOther(routes.EligibilityCheckController.notEligible().url)
        )( userInfo ⇒
          // user has gone through journey already this sessions and were found to be eligible
          ifEligible(userInfo, session.confirmedEmail)
        ))

  private def submissionFailureToString(failure: SubmissionFailure): String =
    s"Call to NS&I failed: message ID was ${failure.errorMessageId.getOrElse("-")},  " +
      s"error was ${failure.errorMessage}, error detail was ${failure.errorDetail}}"


}

object RegisterController {

  private[controllers] implicit class NSIUserInfoOps(val nsiUserInfo: NSIUserInfo) extends AnyVal {
    def updateEmail(email: String): NSIUserInfo =
      nsiUserInfo.copy(contactDetails = nsiUserInfo.contactDetails.copy(email = email))
  }

}
