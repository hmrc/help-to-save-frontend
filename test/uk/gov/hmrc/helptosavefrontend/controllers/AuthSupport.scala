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

import org.joda.time.LocalDate
import org.scalamock.handlers.CallHandler3
import uk.gov.hmrc.auth.core.authorise.{ConfidenceLevel, Enrolment, EnrolmentIdentifier, Enrolments, Predicate}
import uk.gov.hmrc.auth.core.retrieve.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.{AuthProviders, ItmpAddress, ItmpName, Name, Retrieval, Retrievals, ~}
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.UserRetrievals
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait AuthSupport extends TestSupport {

  val mockAuthConnector = mock[FrontendAuthConnector]

  val enrolment = Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", "WM123456C")), "activated", ConfidenceLevel.L200)
  val name = Name(Some("Tyrion"), Some("Lannister"))
  val email: Option[String] = Some("tyrion_lannister@gmail.com")
  val noEmail: Option[String] = None
  val dob: Option[LocalDate] = Some(LocalDate.parse("1970-01-01"))
  val itmpName = ItmpName(Some("Tyrion"), Some("Lannister"), Some("Lannister"))
  val itmpDob: Option[LocalDate] = Some(LocalDate.parse("1970-01-01"))
  val itmpAddress = ItmpAddress(Some("Casterly Rock"), Some("The Westerlands"), Some("Westeros"),
                                None, None, Some("BA148FY"), Some("GB"), Some("GB"))
  val mockedRetrievals = new ~(new ~(new ~(new ~(new ~(new ~(name, email), dob), itmpName), itmpDob), itmpAddress), Enrolments(Set(enrolment)))
  val mockedMissingUserInfo = new ~(new ~(new ~(new ~(new ~(new ~(name, noEmail), dob), itmpName), itmpDob), itmpAddress.copy(line1 = None)), Enrolments(Set(enrolment)))
  val mockedMissingNinoEnrolment = new ~(new ~(new ~(new ~(new ~(new ~(name, noEmail), dob), itmpName), itmpDob), itmpAddress.copy(line1 = None)), Enrolments(Set()))

  def mockAuthResultWithFail(ex: Throwable): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier))
      .expects(AuthProviders(GovernmentGateway), *, *)
      .returning(Future.failed(ex))

  def mockAuthWithRetrievalsWithFail(predicate: Predicate)(ex: Throwable): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Enrolments])(_: HeaderCarrier))
      .expects(predicate, *, *)
      .returning(Future.failed(ex))

  def mockAuthWithRetrievalsWithSuccess[A, B](predicate: Predicate)(result: Name ~ Option[String] ~ Option[LocalDate] ~ ItmpName ~ Option[LocalDate] ~ ItmpAddress ~ Enrolments): CallHandler3[Predicate, Retrieval[~[~[~[~[~[~[Name, Option[String]], Option[LocalDate]], ItmpName], Option[LocalDate]], ItmpAddress], Enrolments]], HeaderCarrier, Future[~[~[~[~[~[~[Name, Option[String]], Option[LocalDate]], ItmpName], Option[LocalDate]], ItmpAddress], Enrolments]]] =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Name ~ Option[String] ~ Option[LocalDate] ~ ItmpName ~ Option[LocalDate] ~ ItmpAddress ~ Enrolments])(_: HeaderCarrier))
      .expects(predicate, UserRetrievals and Retrievals.authorisedEnrolments, *)
      .returning(Future.successful(result))

}
