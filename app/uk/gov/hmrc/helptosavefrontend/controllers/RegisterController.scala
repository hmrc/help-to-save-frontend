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

import cats.data.EitherT
import cats.instances.either._
import cats.instances.future._
import cats.instances.option._
import cats.syntax.either._
import cats.syntax.traverse._
import com.fasterxml.jackson.databind.JsonNode
import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.core.report.ProcessingReport
import com.github.fge.jsonschema.main.{JsonSchemaFactory, JsonValidator}
import com.google.inject.Inject
import configs.syntax._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Request}
import play.api.{Application, Logger}
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.SubmissionFailure
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.JSONValidationFeature._
import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.OAuthConfiguration
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.helptosavefrontend.util.{FEATURE, NINO}
import uk.gov.hmrc.helptosavefrontend.views
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RegisterController @Inject()(val messagesApi: MessagesApi,
                                   helpToSaveService: HelpToSaveService,
                                   sessionCacheConnector: SessionCacheConnector)(implicit app: Application, ec: ExecutionContext)
  extends HelpToSaveAuth(app) with I18nSupport {


  private[controllers] val oauthConfig = app.configuration.underlying.get[OAuthConfiguration]("oauth").value

  def getAuthorisation: Action[AnyContent] = authorisedForHtsWithEnrolments {
    implicit request ⇒
      implicit userUrlWithNino ⇒
        Future.successful(redirectForAuthorisationCode(request, userUrlWithNino))
  }

  def confirmDetails(code: Option[String],
                     error: Option[String],
                     error_description: Option[String],
                     error_code: Option[String]): Action[AnyContent] =
    (code, error) match {
      case (Some(authorisationCode), _) ⇒
        authorisedForHtsWithEnrolments {
          implicit request ⇒
            implicit maybeNino ⇒
              val userInfo: EitherT[Future, String, EligibilityResult] = for {
                nino ← EitherT.fromOption[Future](maybeNino, "could not retrieve either userDetailsUrl or NINO from auth")
                eligible ← helpToSaveService.checkEligibility(nino, authorisationCode)
                nsiUserInfo ← toNSIUserInfo(eligible)
                _ <- EitherT.fromEither[Future](validateOutGoingUserInfo(nsiUserInfo))
                _ ← writeToKeyStore(nsiUserInfo)
              } yield eligible

              userInfo.fold(
                error ⇒ {
                  Logger.error(s"Could not perform eligibility check: $error")
                  InternalServerError("")
                }, _.result.fold(
                  SeeOther(routes.RegisterController.notEligible().url))(
                  userDetails ⇒ Ok(views.html.register.confirm_details(userDetails)))
              )
        }

      case (_, Some(e)) ⇒
        Logger.error(s"Could not get authorisation code: $e. Error description was" +
          s"${error_description.getOrElse("-")}, error code was ${error_code.getOrElse("-")}")
        // TODO: do something better
        Action {
          InternalServerError(s"Could not get authorisation code: $e")
        }

      case _ ⇒
        // we should never reach here - we shouldn't have a successful code and an error at the same time
        Logger.error("Inconsistent result found when attempting to retrieve an authorisation code")
        Action { InternalServerError("")
        }
    }

  def getCreateAccountHelpToSavePage: Action[AnyContent] = authorisedForHtsWithConfidence {
    implicit request ⇒
      Future.successful(Ok(views.html.register.create_account_help_to_save()))
  }

  def validateOutGoingUserInfo(userInfo: Option[NSIUserInfo]): Either[String, Option[NSIUserInfo]] = {
    userInfo match {
      case None => Right(None)
      case Some(ui) =>
        FEATURE("outgoing-json-validation", app.configuration, featureLogger).thenOrElse(
          for {
            t0 <- validateUserInfoAgainstSchema(ui, validationSchema)
            t1 <- before1800(ui)
            t2 <- futureDate(ui)
          } yield t2,
          //_ => validateUserInfoAgainstSchema(ui, validationSchema),
          Right(userInfo))
    }
  }

  def createAccountHelpToSave: Action[AnyContent] = authorisedForHtsWithConfidence {
    implicit request ⇒
      val result = for {
        userInfo ← retrieveUserInfo()
        _ ← helpToSaveService.createAccount(userInfo).leftMap(submissionFailureToString)
      } yield userInfo

      // TODO: plug in actual pages below
      result.fold(
        error ⇒ {
          Logger.error(s"Could not create account: $error")
          Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page(s"Account creation failed: $error"))
        },
        info ⇒ {
          Logger.debug(s"Successfully created account for ${info.nino}")
          Ok(uk.gov.hmrc.helptosavefrontend.views.html.core.stub_page("Successfully created account"))
        }
      )
  }

  def accessDenied: Action[AnyContent] = Action.async {
    implicit request ⇒
      Future.successful(Ok(views.html.access_denied()))
  }

  val notEligible: Action[AnyContent] = Action.async { implicit request ⇒
    Future.successful(Ok(views.html.core.not_eligible()))
  }

  private def retrieveUserInfo()(implicit hc: HeaderCarrier): EitherT[Future, String, NSIUserInfo] = {
    val session = sessionCacheConnector.get
    val userInfo: Future[Option[NSIUserInfo]] = session.map(_.flatMap(_.userInfo))

    EitherT(
      userInfo.map(_.fold[Either[String, NSIUserInfo]](
        Left("Session cache did not contain session data"))(Right(_))))
  }

  /**
    * Writes the user info to key-store if it exists and returns the associated [[CacheMap]]. If the user info
    * is not defined, don't do anything and return [[None]]. Any errors during writing to key-store are
    * captured as a [[String]] in the [[Either]].
    */
  private def writeToKeyStore(userDetails: Option[NSIUserInfo])(implicit hc: HeaderCarrier): EitherT[Future, String, Option[CacheMap]] = {
    // write to key-store
    val cacheMapOption: Option[Future[CacheMap]] =
      userDetails.map { details ⇒ sessionCacheConnector.put(HTSSession(Some(details))) }

    // use traverse to swap the option and future
    val cacheMapFuture: Future[Option[CacheMap]] =
      cacheMapOption.traverse[Future, CacheMap](identity)

    EitherT(
      cacheMapFuture.map[Either[String, Option[CacheMap]]](Right(_))
        .recover { case e ⇒ Left(s"Could not write to key-store: ${e.getMessage}") }
    )
  }

  private type EitherOrString[A] = Either[String, A]

  private def toNSIUserInfo(eligibilityResult: EligibilityResult): EitherT[Future, String, Option[NSIUserInfo]] = {
    val conversion: Option[Either[String, NSIUserInfo]] = eligibilityResult.result.map { result ⇒
      val validation = NSIUserInfo(result)
      validation.toEither.leftMap(
        errors ⇒ s"User info did not pass NS&I validity checks: ${errors.toList.mkString("; ")}"
      )
    }

    // use sequence to push the option into the either
    val result: Either[String, Option[NSIUserInfo]] = conversion.sequence[EitherOrString, NSIUserInfo]

    EitherT.fromEither[Future](result)
  }

  private def submissionFailureToString(failure: SubmissionFailure): String =
    s"Call to NS&I failed: message ID was ${failure.errorMessageId.getOrElse("-")}, " +
      s"error was ${failure.errorMessage}, error detail was ${failure.errorDetail}}"


  private lazy val redirectForAuthorisationCode =
    if (oauthConfig.enabled) {
      { (_: Request[AnyContent], _: Option[NINO]) ⇒
        Logger.info("Received request to get user details: redirecting to oauth obtain authorisation code")

        // we need to get an authorisation token from OAuth - redirect to OAuth here. When the authorisation
        // is done they'll redirect to the callback url we give them
        Redirect(
          oauthConfig.url,
          Map(
            "client_id" -> Seq(oauthConfig.clientID),
            "scope" -> oauthConfig.scopes,
            "response_type" -> Seq("code"),
            "redirect_uri" -> Seq(oauthConfig.callbackURL)
          ))
      }
    } else {
      { (request: Request[AnyContent], userDetailsUrlWithNino: Option[NINO]) ⇒
        // if the redirect to oauth is not enabled redirect straight to our 'confirm-details' endpoint
        // using the NINO as the authorisation code
        implicit val r = request

        userDetailsUrlWithNino.fold {
          Logger.error("NINO or user details URI not available")
          Redirect(routes.RegisterController.notEligible().absoluteURL())
         } { nino ⇒
          Logger.info(s"Received request to get user details: redirecting to get user details using NINO $nino as authorisation code")
          Redirect(routes.RegisterController.confirmDetails(Some(nino), None, None, None).absoluteURL())
        }
      }
    }

  private[controllers] def validateUserInfoAgainstSchema(userInfo: NSIUserInfo, schema: JsonNode): Either[String, Option[NSIUserInfo]] = {

    val userInfoJson = JsonLoader.fromString(Json.toJson(userInfo).toString)
    try {
      val report: ProcessingReport = jsonValidator.validate(schema, userInfoJson)
      if (report.isSuccess) Right(Some(userInfo)) else Left(report.toString)
    } catch {
      case e: Exception => Left(e.getMessage)
    }
  }

  private[controllers] def futureDate(userInfo: NSIUserInfo): Either[String, Option[NSIUserInfo]] = {
    val today = java.time.LocalDate.now()
    if (userInfo.dateOfBirth.isAfter(today)) Left("FEATURE outgoing-json-validation: Date of birth in the future") else Right(Some(userInfo))
  }

  private[controllers] def before1800(userInfo: NSIUserInfo): Either[String, Option[NSIUserInfo]] = {
    val year = userInfo.dateOfBirth.getYear
    if (year < 1800) Left("FEATURE outgoing-json-validation: Date of birth before 1800") else Right(Some(userInfo))
  }
}

object RegisterController {

  object JSONValidationFeature {
    //For ICD v 1.2
    private[controllers] lazy val validationSchemaStr =
      """
        |{
        |  "$schema": "http://json-schema.org/schema#",
        |  "description": "A JSON schema to validate JSON as described in PPM-30048-UEM009-ICD001-HTS-HMRC-Interfaces v1.2.docx",
        |
        |  "type" : "object",
        |  "additionalProperties": false,
        |  "required": ["forename", "surname", "dateOfBirth", "contactDetails", "registrationChannel", "nino"],
        |  "properties" : {
        |    "forename" : {
        |      "type" : "string",
        |      "minLength": 1,
        |      "maxLength": 26,
        |      "pattern": "^[a-zA-Z][,a-zA-Z\\s\\.\\&\\-]*$"
        |    },
        |    "surname": {
        |      "type": "string",
        |      "minLength": 1,
        |      "maxLength": 300,
        |      "pattern": "^[a-zA-Z][`,'a-zA-Z\\s\\.\\&\\-]*$"
        |    },
        |    "dateOfBirth": {
        |      "type": "string",
        |      "minLength": 8,
        |      "maxLength": 8,
        |      "pattern": "^[0-9]{4}01|02|03|04|05|06|07|08|09|10|11|12[0-9]{2}$"
        |    },
        |    "contactDetails": {
        |      "type": "object",
        |      "additionalProperties": false,
        |      "required": ["address1", "address2", "postcode", "communicationPreference"],
        |      "properties": {
        |        "countryCode": {
        |          "type": "string",
        |          "minLength": 2,
        |          "maxLength": 2,
        |          "pattern": "[A-Z][A-Z]"
        |        },
        |        "address1": {
        |          "type": "string",
        |          "maxLength": 35
        |        },
        |        "address2": {
        |          "type": "string",
        |          "maxLength": 35
        |        },
        |        "address3": {
        |          "type": "string",
        |          "maxLength": 35
        |        },
        |        "address4": {
        |          "type": "string",
        |          "maxLength": 35
        |        },
        |        "address5": {
        |          "type": "string",
        |          "maxLength": 35
        |        },
        |        "postcode": {
        |          "type": "string",
        |          "maxLength": 10
        |        },
        |        "communicationPreference": {
        |          "type": "string",
        |          "minLength": 2,
        |          "maxLength": 2,
        |          "pattern": "00|02"
        |        },
        |        "phoneNumber": {
        |          "type": "string",
        |          "maxLength": 15
        |        },
        |        "email": {
        |          "type": "string",
        |          "maxLength": 254,
        |          "pattern": "^.{1,64}@.{1,252}$"
        |        }
        |      }
        |    },
        |    "registrationChannel": {
        |      "type": "string",
        |      "maxLength": 10,
        |      "pattern": "^online$|^callCentre$"
        |    },
        |    "nino" : {
        |      "type" : "string",
        |      "minLength": 9,
        |      "maxLength": 9,
        |      "pattern": "^(([A-CEGHJ-PR-TW-Z][A-CEGHJ-NPR-TW-Z])([0-9]{2})([0-9]{2})([0-9]{2})([A-D]{1})|((XX)(99)(99)(99)(X)))$"
        |    }
        |  }
        |}
      """.stripMargin

    lazy val validationSchema = JsonLoader.fromString(validationSchemaStr)
    lazy val featureLogger = Logger("outgoing-json-validation")
    lazy val jsonValidator: JsonValidator = JsonSchemaFactory.byDefault().getValidator
  }

  // details required to get an authorisation token from OAuth
  private[controllers] case class OAuthConfiguration(enabled: Boolean, url: String, clientID: String, callbackURL: String, scopes: List[String])

}
