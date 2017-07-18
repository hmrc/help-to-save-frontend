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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cats.data.EitherT
import cats.instances.future._
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.fge.jackson.JsonLoader
import com.github.fge.jsonschema.core.report.ProcessingReport
import play.api.http.Status
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import play.api.mvc.{Result => PlayResult}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.connectors.NSIConnector.{SubmissionFailure, SubmissionSuccess}
import uk.gov.hmrc.helptosavefrontend.connectors._
import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.JSONValidationFeature._
import uk.gov.hmrc.helptosavefrontend.controllers.RegisterController.OAuthConfiguration
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.AuthWithConfidence
import uk.gov.hmrc.helptosavefrontend.models.MissingUserInfo.{Contact, Email}
import uk.gov.hmrc.helptosavefrontend.models._
import uk.gov.hmrc.helptosavefrontend.services.HelpToSaveService
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class RegisterControllerSpec extends TestSupport {

  private val mockHtsService = mock[HelpToSaveService]

  val nino = "WM123456C"

  private val enrolment = Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", nino)), "activated", ConfidenceLevel.L200)
  private val enrolments = Enrolments(Set(enrolment))

  private val mockAuthConnector = mock[PlayAuthConnector]
  val mockSessionCacheConnector: SessionCacheConnector = mock[SessionCacheConnector]
  val testOAuthConfiguration = OAuthConfiguration(true, "url", "client-ID", "callback", List("scope1", "scope2"))

  val oauthAuthorisationCode = "authorisation-code"

  val register = new RegisterController(
    fakeApplication.injector.instanceOf[MessagesApi],
    mockHtsService,
    mockSessionCacheConnector)(
    fakeApplication, ec) {
    override val oauthConfig = testOAuthConfiguration
    override lazy val authConnector = mockAuthConnector
  }

  def mockEligibilityResult(nino: String, authorisationCode: String)(result: Either[MissingUserInfos, Option[UserInfo]]): Unit =
    (mockHtsService.checkEligibility(_: String, _: String)(_: HeaderCarrier))
      .expects(nino,authorisationCode, *)
      .returning(EitherT.pure(EligibilityCheckResult(result)))

  def failEligibilityResult(nino: String, authorisationCode: String): Unit =
    (mockHtsService.checkEligibility(_: String, _: String)(_: HeaderCarrier))
      .expects(nino,authorisationCode, *)
      .returning(EitherT.fromEither(Left("unexpected error during eligibility check")))

  def mockSessionCacheConnectorPut(result: Either[String, CacheMap]): Unit =
    (mockSessionCacheConnector.put(_: HTSSession)(_: Writes[HTSSession], _: HeaderCarrier))
      .expects(*, *, *)
      .returning(result.fold(
        e ⇒ Future.failed(new Exception(e)),
        Future.successful))

  def mockSessionCacheConnectorGet(mockHtsSession: Option[HTSSession]): Unit =
    (mockSessionCacheConnector.get(_: HeaderCarrier, _: Reads[HTSSession]))
      .expects(*, *)
      .returning(Future.successful(mockHtsSession))

  def mockCreateAccount(nSIUserInfo: NSIUserInfo)(response: Either[SubmissionFailure, SubmissionSuccess] = Right(SubmissionSuccess())): Unit =
    (mockHtsService.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nSIUserInfo, *, *)
      .returning(EitherT.fromEither[Future](response))

  def mockPlayAuthWithRetrievals[A, B](predicate: Predicate)(result: Enrolments): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Enrolments])(_: HeaderCarrier))
      .expects(predicate, *, *)
      .returning(Future.successful(result))

  def mockPlayAuthWithWithConfidence(): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier))
      .expects(AuthWithConfidence, *, *)
      .returning(Future.successful(()))


  "The RegisterController" when {

    "checking eligibility" must {
      def doConfirmDetailsRequest(): Future[PlayResult] = register.getAuthorisation(FakeRequest())

      def doConfirmDetailsCallbackRequest(authorisationCode: String): Future[PlayResult] =
        register.confirmDetails(Some(authorisationCode), None, None, None)(FakeRequest())


      "redirect to confirm-details with the NINO as the authorisation code if redirects to OAUTH are disabled" in {
        val register = new RegisterController(
          fakeApplication.injector.instanceOf[MessagesApi],
          mockHtsService,
          mockSessionCacheConnector)(
          fakeApplication, ec) {
          override val oauthConfig = testOAuthConfiguration.copy(enabled = false)
          override lazy val authConnector = mockAuthConnector
        }

        mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)

        implicit val request = FakeRequest()
        val result = register.getAuthorisation(request)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.confirmDetails(Some(nino), None, None, None).absoluteURL())
      }

      "return the not eligible page if redirects to OAUTH are disabled and a NINO is not available" in {
        val register = new RegisterController(
          fakeApplication.injector.instanceOf[MessagesApi],
          mockHtsService,
          mockSessionCacheConnector)(
          fakeApplication, ec) {
          override val oauthConfig = testOAuthConfiguration.copy(enabled = false)
          override lazy val authConnector = mockAuthConnector
        }

        mockPlayAuthWithRetrievals(AuthWithConfidence)(Enrolments(Set.empty[Enrolment]))

        implicit val request = FakeRequest()
        val result = register.getAuthorisation(request)
        status(result) shouldBe Status.SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.RegisterController.notEligible().absoluteURL())
      }

      "redirect to OAuth to get an access token if enabled" in {
        mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)

        val result = doConfirmDetailsRequest()
        status(result) shouldBe Status.SEE_OTHER

        val (url, params) = redirectLocation(result).get.split('?').toList match {
          case u :: p :: Nil ⇒
            val paramList = p.split('&').toList
            val keyValueSet = paramList.map(_.split('=').toList match {
              case key :: value :: Nil ⇒ key → value
              case _ ⇒ fail(s"Could not parse query parameters: $p")
            }).toSet

            u → keyValueSet

          case _ ⇒ fail("Could not parse URL with query parameters")
        }

        url shouldBe testOAuthConfiguration.url
        params shouldBe (testOAuthConfiguration.scopes.map("scope" → _).toSet ++ Set(
          "client_id" -> testOAuthConfiguration.clientID,
          "response_type" -> "code",
          "redirect_uri" -> testOAuthConfiguration.callbackURL
        ))
      }

      "return a 500 if there is an error while getting the authorisation token" in {
        val result = register.confirmDetails(None, Some("uh oh"), None, None)(FakeRequest())
        status(result) shouldBe 500
      }

      "return user details if the user is eligible for help-to-save" in {
        val user = validUserInfo
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
          mockEligibilityResult(nino, oauthAuthorisationCode)(Right(Some(user)))
          mockSessionCacheConnectorPut(Right(CacheMap("1", Map.empty[String, JsValue])))
        }

        val responseFuture: Future[PlayResult] = doConfirmDetailsCallbackRequest(oauthAuthorisationCode)
        val result = Await.result(responseFuture, 5.seconds)

        status(result) shouldBe Status.OK

        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")

        val html = contentAsString(result)

        html should include(user.forename)
        html should include(user.email)
        html should include(user.nino)
      }

      "display a 'Not Eligible' page if the user is not eligible" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
          mockEligibilityResult(nino, oauthAuthorisationCode)(Right(None))
        }

        val result = doConfirmDetailsCallbackRequest(oauthAuthorisationCode)

        status(result) shouldBe Status.SEE_OTHER

        redirectLocation(result) shouldBe Some("/help-to-save/register/not-eligible")
      }

      "use the validate function to check user info against empty schemas" in {
        val schema = JsonLoader.fromString("{}")

        register.validateUserInfoAgainstSchema(validNSIUserInfo, schema) shouldBe Right(Some(validNSIUserInfo))
      }

      "use the validate function to check user info against arbitrary schemas (example 1)" in {
        val schema = JsonLoader.fromString("""{"type": "object", "properties": {"forename": {"type": "string"}, "surname": {"type": "string"}}}""")

        register.validateUserInfoAgainstSchema(validNSIUserInfo, schema) shouldBe Right(Some(validNSIUserInfo))
      }

      "use the validate function to check arbitrary JSON against arbitrary schemas (example 2)" in {
        val schema = JsonLoader.fromString("""{"type": "object", "properties": {"forename": {"type": "number"}, "food": {"type": "string"}}}""")

        register.validateUserInfoAgainstSchema(validNSIUserInfo, schema).isLeft shouldBe true
      }

      "If the outgoing-json validation feature is a schema as defined by json-schema.org" in {
        val schemaJsonNode: JsonNode = JsonLoader.fromString(validationSchemaStr)

        schemaJsonNode.isObject shouldBe true
      }

      "If the outgoing-json validation feature detects an exception, a left with the exception message is produced" in {
        register.validateUserInfoAgainstSchema(validNSIUserInfo, null).isLeft shouldBe true
      }

      "If the outgoing-json validation feature detects a birth date prior to 1800 it returns a left" in {
        val date = LocalDate.parse("17990505", DateTimeFormatter.BASIC_ISO_DATE)
        val oldUser = validNSIUserInfo copy (dateOfBirth = date)
        register.before1800(oldUser).isLeft shouldBe true
      }

      "If the outgoing-json validation feature detects a birth date just after to 1800 it returns a right" in {
        val date = LocalDate.parse("18000101", DateTimeFormatter.BASIC_ISO_DATE)
        val oldUser = validNSIUserInfo copy (dateOfBirth = date)
        register.before1800(oldUser).isRight shouldBe true
      }

      "If the outgoing-json validateOutGoingJson function detects a birth date prior to 1800 it returns a left" in {
        val date = LocalDate.parse("17990505", DateTimeFormatter.BASIC_ISO_DATE)
        val oldUser = validNSIUserInfo copy (dateOfBirth = date)
        register.validateCreateAccountJsonSchema(Some(oldUser)).isLeft shouldBe true
      }

      "If the outgoing-json futureDate function detects a birth date in the future it returns a left " in {
        val today = java.time.LocalDate.now()
        val futureUser = validNSIUserInfo copy (dateOfBirth = today)
        register.validateCreateAccountJsonSchema(Some(futureUser)).isRight shouldBe true
      }

      "If the outgoing-json futureDate function detects a birth date of today it returns a right " in {
        val today = java.time.LocalDate.now()
        val tomorrow = today.plus(1, java.time.temporal.ChronoUnit.DAYS)
        val futureUser = validNSIUserInfo copy (dateOfBirth = tomorrow)
        register.validateCreateAccountJsonSchema(Some(futureUser)).isLeft shouldBe true
      }

      "If the outgoing-json validateOutGoingJson function detects a birth date in the future it returns a left " in {
        val today = java.time.LocalDate.now()
        val tomorrow = today.plus(1, java.time.temporal.ChronoUnit.DAYS)
        val futureUser = validNSIUserInfo copy (dateOfBirth = tomorrow)
        register.futureDate(futureUser).isLeft shouldBe true
      }

      "when given a NSIUserInfo that meets the json validation schema, return a zero length report" in {
        import scala.collection.JavaConversions._

        val userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        report.iterator().toSeq.length shouldBe 0
      }

      "when given a NSIUserInfo that the json validation schema reports that the forename is the wrong type, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.asInstanceOf[ObjectNode].put("forename", 0)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: forename is wrong type, needs to be a string"
      }

      "when given a NSIUserInfo that the json validation schema reports that the forename is too short, return a message" in {
        import scala.collection.JavaConversions._

        val nsiWithShortForename = validNSIUserInfo copy (forename = "")
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithShortForename).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithShortForename).isLeft shouldBe true
        register.classify(messages(0), nsiWithShortForename).fold(identity, _ => "") shouldBe "For NINO WM123456C: forename is less than 1 char, needs to be at least 1 char"
      }

      "when given a NSIUserInfo that the json validation schema reports that the forename is too long, return a message" in {
        import scala.collection.JavaConversions._

        val nsiWithLongForename = validNSIUserInfo copy (forename = "A" * 27)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithLongForename).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithLongForename).isLeft shouldBe true
        register.classify(messages(0), nsiWithLongForename).fold(identity, _ => "") shouldBe "For NINO WM123456C: forename is greater than 26 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the forename is missing" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.asInstanceOf[ObjectNode].remove("forename")
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: forename was mandatory but not supplied"
      }

      "when given a NSIUserInfo that the json validation schema reports that the surname is the wrong type, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.asInstanceOf[ObjectNode].put("surname", 0)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: surname is wrong type, needs to be a string"
      }

      "when given a NSIUserInfo that the json validation schema reports that the surname is too short, return a message" in {
        import scala.collection.JavaConversions._

        val nsiWithShortSurname = validNSIUserInfo copy (surname = "")
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithShortSurname).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithShortSurname).isLeft shouldBe true
        register.classify(messages(0), nsiWithShortSurname).fold(identity, _ => "") shouldBe "For NINO WM123456C: surname is less than 1 char, needs to be at least 1 char"
      }

      "when given a NSIUserInfo that the json validation schema reports that the surname is too long, return a message" in {
        import scala.collection.JavaConversions._

        val nsiWithLongSurname = validNSIUserInfo copy (surname = "A" * 301)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithLongSurname).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithLongSurname).isLeft shouldBe true
        register.classify(messages(0), nsiWithLongSurname).fold(identity, _ => "") shouldBe "For NINO WM123456C: surname is greater than 300 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the surname is missing" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.asInstanceOf[ObjectNode].remove("surname")
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: surname was mandatory but not supplied"
      }

      "when given a NSIUserInfo that the json validation schema reports that the date of birth is the wrong type, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.asInstanceOf[ObjectNode].put("dateOfBirth", 0)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: date of birth is wrong type, needs to be a string"
      }

      "when given a NSIUserInfo that the json validation schema reports that the dateOfBirth is too short, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.asInstanceOf[ObjectNode].put("dateOfBirth", "1800525")
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 2
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: date of birth is less than 8 chars, needs to be 8 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the dateOfBirth is too long, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.asInstanceOf[ObjectNode].put("dateOfBirth", "180000525")
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 2
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: date of birth is greater than 8 chars, needs to be 8 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the dateOfBirth does not meet the regex, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.asInstanceOf[ObjectNode].put("dateOfBirth", "18oo0525")
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: date of birth contained an unrecognised char sequence"
      }

      "when given a NSIUserInfo that the json validation schema reports that the dateOfBirth is missing, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.asInstanceOf[ObjectNode].remove("dateOfBirth")
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: date of birth was mandatory but not supplied"
      }

      "when given a NSIUserInfo that the json validation schema reports that the country code is the wrong type, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.path("contactDetails").asInstanceOf[ObjectNode].put("countryCode", 0)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: country code is wrong type, needs to be a string"
      }

      "when given a NSIUserInfo that the json validation schema reports that the country code is too short, return a message" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithShortCountryCode = nsiValidContactDetails copy (countryCode = Some("G"))
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithShortCountryCode)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 2
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: country code is less than 2 chars, needs to be 2 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the country code is too long, return a message" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithLongCountryCode = nsiValidContactDetails copy (countryCode = Some("GRG"))
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithLongCountryCode)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: country code is greater than 2 chars, needs to be 2 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the country code does not meet the regex, return a message" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithLongCountryCode = nsiValidContactDetails copy (countryCode = Some("--"))
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithLongCountryCode)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: country code contained an unrecognised char sequence"
      }

      "when given a NSIUserInfo that the json validation schema reports that the address1 field is the wrong type, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.path("contactDetails").asInstanceOf[ObjectNode].put("address1", 0)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: address1 field is wrong type, needs to be a string"
      }

      "when given a NSIUserInfo that the json validation schema reports that the address1 field is too long" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithLongAddress1 = nsiValidContactDetails copy (address1 = "A" * 36)
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithLongAddress1)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: address1 field is greater than 35 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the address1 field is missing, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.path("contactDetails").asInstanceOf[ObjectNode].remove("address1")
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: address1 field was mandatory but not supplied"
      }

      "when given a NSIUserInfo that the json validation schema reports that the address2 field is the wrong type, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.path("contactDetails").asInstanceOf[ObjectNode].put("address2", 0)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: address2 field is wrong type, needs to be a string"
      }

      "when given a NSIUserInfo that the json validation schema reports that the address2 field is too long" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithLongAddress2 = nsiValidContactDetails copy (address2 = "A" * 36)
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithLongAddress2)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: address2 field is greater than 35 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the address2 field is missing, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.path("contactDetails").asInstanceOf[ObjectNode].remove("address2")
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: address2 field was mandatory but not supplied"
      }

      "when given a NSIUserInfo that the json validation schema reports that the address3 field is the wrong type, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.path("contactDetails").asInstanceOf[ObjectNode].put("address3", 0)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: address3 field is wrong type, needs to be a string"
      }


      "when given a NSIUserInfo that the json validation schema reports that the address3 field is too long" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithLongAddress3 = nsiValidContactDetails copy (address3 = Some("A" * 36))
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithLongAddress3)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: address3 field is greater than 35 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the address4 field is the wrong type, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.path("contactDetails").asInstanceOf[ObjectNode].put("address4", 0)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: address4 field is wrong type, needs to be a string"
      }

      "when given a NSIUserInfo that the json validation schema reports that the address4 field is too long" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithLongAddress4 = nsiValidContactDetails copy (address3 = Some("A" * 35), address4 = Some("A" * 36))
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithLongAddress4)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: address4 field is greater than 35 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the address5 field is the wrong type, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.path("contactDetails").asInstanceOf[ObjectNode].put("address5", 0)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: address5 field is wrong type, needs to be a string"
      }

      "when given a NSIUserInfo that the json validation schema reports that the address5 field is too long" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithLongAddress5 = nsiValidContactDetails copy (address3 = Some("A" * 35), address4 = Some("A" * 35), address5 = Some("A" * 36))
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithLongAddress5)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: address5 field is greater than 35 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the postcode field is the wrong type, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.path("contactDetails").asInstanceOf[ObjectNode].put("postcode", 0)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: postcode field is wrong type, needs to be a string"
      }

      "when given a NSIUserInfo that the json validation schema reports that the postcode field is too long" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithBadPostcode = nsiValidContactDetails copy (postcode = "P" * 11)
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithBadPostcode)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: postcode is greater than 10 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the postcode is missing, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.path("contactDetails").asInstanceOf[ObjectNode].remove("postcode")
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: postcode was mandatory but not supplied"
      }

      "when given a NSIUserInfo that the json validation schema reports that the communicationPreference field is the wrong type, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.path("contactDetails").asInstanceOf[ObjectNode].put("communicationPreference", 0)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: communication preference field is wrong type, needs to be a string"
      }

      "when given a NSIUserInfo that the json validation schema reports that the communicationPreference field is too short" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithBadCommsPref = nsiValidContactDetails copy (communicationPreference = "")
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithBadCommsPref)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 2
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: communications preference is less than 2 chars, needs to be 2 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the communicationPreference field is too long" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithBadCommsPref = nsiValidContactDetails copy (communicationPreference = "AAA")
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithBadCommsPref)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 2
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: communications preference is greater than 2 chars, needs to be 2 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the communicationPreference field does not meet regex" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithBadCommsPref = nsiValidContactDetails copy (communicationPreference = "01")
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithBadCommsPref)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: communications preference contained an unrecognised char sequence"
      }

      "when given a NSIUserInfo that the json validation schema reports that the communicationPreference field is missing, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.path("contactDetails").asInstanceOf[ObjectNode].remove("communicationPreference")
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: communications preference was mandatory but not supplied"
      }

      "when given a NSIUserInfo that the json validation schema reports that the phone number field is the wrong type, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.path("contactDetails").asInstanceOf[ObjectNode].put("phoneNumber", 0)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: phone number field is wrong type, needs to be a string"
      }


      "when given a NSIUserInfo that the json validation schema reports that the phone number is too long" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithBadPhoneNumber = nsiValidContactDetails copy (phoneNumber = Some("A" * 16))
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithBadPhoneNumber)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: phone number is greater than 15 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the email address is too long" in {
        import scala.collection.JavaConversions._

        val contactDetailsWithBadEmail = nsiValidContactDetails copy (email = "A" * 63 + "@" + "A" * 251)
        val nsiWithBadContactDetails = validNSIUserInfo copy (contactDetails = contactDetailsWithBadEmail)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadContactDetails).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithBadContactDetails).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadContactDetails).fold(identity, _ => "") shouldBe "For NINO WM123456C: email address is greater than 254 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the registration channel is too long" in {
        import scala.collection.JavaConversions._

        val nsiWithBadRegistrationChannel = validNSIUserInfo copy (registrationChannel = "A" * 11)
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadRegistrationChannel).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 2
        register.classify(messages(0), nsiWithBadRegistrationChannel).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadRegistrationChannel).fold(identity, _ => "") shouldBe "For NINO WM123456C: registration channel is greater than 10 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the registration channel does not meet regex, return a message" in {
        import scala.collection.JavaConversions._

        val nsiWithBadRegistrationChannel = validNSIUserInfo copy (registrationChannel = "offline")
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadRegistrationChannel).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithBadRegistrationChannel).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadRegistrationChannel).fold(identity, _ => "") shouldBe "For NINO WM123456C: registration channel contained an unrecognised char sequence"
      }

      "when given a NSIUserInfo that the json validation schema reports that the registration channel is missing, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.asInstanceOf[ObjectNode].remove("registrationChannel")
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "For NINO WM123456C: registration channel was mandatory but not supplied"
      }

      "when given a NSIUserInfo that the json validation schema reports that the nino is too short" in {
        import scala.collection.JavaConversions._

        val nsiWithBadNino = validNSIUserInfo copy (nino = "WM23456C")
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadNino).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 2
        register.classify(messages(0), nsiWithBadNino).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadNino).fold(identity, _ => "") shouldBe "For NINO WM23456C: nino is less than 9 chars, needs to be 9 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the nino is too long" in {
        import scala.collection.JavaConversions._

        val nsiWithBadNino = validNSIUserInfo copy (nino = "WM1234567C")
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadNino).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 2
        register.classify(messages(0), nsiWithBadNino).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadNino).fold(identity, _ => "") shouldBe "For NINO WM1234567C: nino is greater than 9 chars, needs to be 9 chars"
      }

      "when given a NSIUserInfo that the json validation schema reports that the nino does not meet the validation regex" in {
        import scala.collection.JavaConversions._

        val nsiWithBadNino = validNSIUserInfo copy (nino = "WMAA3456C")
        val userInfoJson = JsonLoader.fromString(Json.toJson(nsiWithBadNino).toString)
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), nsiWithBadNino).isLeft shouldBe true
        register.classify(messages(0), nsiWithBadNino).fold(identity, _ => "") shouldBe "For NINO WMAA3456C: nino contained an unrecognised char sequence"
      }

      "when given a NSIUserInfo that the json validation schema reports that the nino is missing, return a message" in {
        import scala.collection.JavaConversions._

        var userInfoJson = JsonLoader.fromString(Json.toJson(validNSIUserInfo).toString)
        userInfoJson.asInstanceOf[ObjectNode].remove("nino")
        val report: ProcessingReport = jsonValidator.validate(validationSchema, userInfoJson)
        val messages = report.iterator().toSeq
        messages.length shouldBe 1
        register.classify(messages(0), validNSIUserInfo).isLeft shouldBe true
        register.classify(messages(0), validNSIUserInfo).fold(identity, _ => "") shouldBe "Nino was mandatory but not supplied"
      }

      "report missing user info back to the user" in {
        inSequence {
          mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
          mockEligibilityResult(nino, oauthAuthorisationCode)(Left(MissingUserInfos(Set(Email, Contact))))
        }

        val responseFuture: Future[PlayResult] = doConfirmDetailsCallbackRequest(oauthAuthorisationCode)
        val result = Await.result(responseFuture, 5.seconds)

        status(result) shouldBe Status.OK

        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")

        val html = contentAsString(result)

        html should include("Email")
        html should include("Contact")
      }

      "return an error" must {

        def isError(result: Future[PlayResult]): Boolean =
          status(result) == 500

        // test if the given mock actions result in an error when `confirm_details` is called
        // on the controller
        def test(mockActions: ⇒ Unit): Unit = {
          mockActions
          val result = doConfirmDetailsCallbackRequest(oauthAuthorisationCode)
          isError(result) shouldBe true
        }

        "the nino is not available" in {
          test(
            mockPlayAuthWithRetrievals(AuthWithConfidence)(Enrolments(Set.empty[Enrolment]))
          )
        }

        "the eligibility check call returns with an error" in {
          test(
            inSequence {
              mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
              failEligibilityResult(nino, oauthAuthorisationCode)
            })
        }

        "if the user details fo not pass NS&I validation checks" in {
          val user = validUserInfo.copy(forename = " space-at-beginning")
          test(inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
            mockEligibilityResult(nino, oauthAuthorisationCode)(Right(Some(user)))
          })
        }

        "there is an error writing to the session cache" in {
          test(inSequence {
            mockPlayAuthWithRetrievals(AuthWithConfidence)(enrolments)
            mockEligibilityResult(nino, oauthAuthorisationCode)(Right(Some(validUserInfo)))
            mockSessionCacheConnectorPut(Left("Bang"))
          })
        }
      }
    }


    "handling a getCreateAccountHelpToSave" must {

      "return 200" in {
        mockPlayAuthWithWithConfidence()
        val result = register.getCreateAccountHelpToSavePage(FakeRequest())
        status(result) shouldBe Status.OK
        contentType(result) shouldBe Some("text/html")
        charset(result) shouldBe Some("utf-8")
      }
    }

    "creating an account" must {
      def doCreateAccountRequest(): Future[PlayResult] = register.createAccountHelpToSave(FakeRequest())

      "retrieve the user info from session cache and post it using " +
        "the help to save service" in {
        inSequence {
          mockPlayAuthWithWithConfidence()
          mockSessionCacheConnectorGet(Some(HTSSession(Some(validNSIUserInfo))))
          mockCreateAccount(validNSIUserInfo)()
        }
        val result = Await.result(doCreateAccountRequest(), 5.seconds)
        status(result) shouldBe Status.OK
      }


      "indicate to the user that the creation was successful if the creation was successful" in {
        inSequence {
          mockPlayAuthWithWithConfidence()
          mockSessionCacheConnectorGet(Some(HTSSession(Some(validNSIUserInfo))))
          mockCreateAccount(validNSIUserInfo)()
        }

        val result = doCreateAccountRequest()
        val html = contentAsString(result)
        html should include("Successfully created account")
      }

      "indicate to the user that the creation was not successful " when {

        "the user details cannot be found in the session cache" in {
          inSequence {
            mockPlayAuthWithWithConfidence()
            mockSessionCacheConnectorGet(None)
          }

          val result = doCreateAccountRequest()
          val html = contentAsString(result)
          html should include("Account creation failed")
        }

        "the help to save service returns with an error" in {
          inSequence {
            mockPlayAuthWithWithConfidence()
            mockSessionCacheConnectorGet(Some(HTSSession(Some(validNSIUserInfo))))
            mockCreateAccount(validNSIUserInfo)(Left(SubmissionFailure(None, "Uh oh", "Uh oh")))
          }

          val result = doCreateAccountRequest()
          val html = contentAsString(result)
          html should include("Account creation failed")
        }
      }
    }
  }
}
