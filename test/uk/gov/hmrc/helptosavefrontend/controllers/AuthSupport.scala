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
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.{retrieve, _}
import uk.gov.hmrc.auth.core.authorise._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.helptosavefrontend.TestSupport
import uk.gov.hmrc.helptosavefrontend.config.FrontendAuthConnector
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.UserRetrievals
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosavefrontend.util.toJavaDate
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

object AuthSupport {

  private implicit class ROps[A, B](val r: ~[A, B]) {
    def and[C](c: C): ~[~[A, B], C] = new ~(r, c)
  }

}

trait AuthSupport extends TestSupport {

  import AuthSupport._

  type UserRetrievalType = Name ~ Option[String] ~ Option[LocalDate] ~ ItmpName ~ Option[LocalDate] ~ ItmpAddress ~ Enrolments ~ Option[String]

  val mockAuthConnector: FrontendAuthConnector = mock[FrontendAuthConnector]

  val nino = "WM123456C"
  val enrolment = Enrolment("HMRC-NI", Seq(EnrolmentIdentifier("NINO", nino)), "activated", ConfidenceLevel.L200)

  val firstName = "Tyrion"
  val lastName = "Lannister"
  val name = Name(Some(firstName), Some(lastName))

  val emailStr = "tyrion_lannister@gmail.com"
  val email: Option[String] = Some(emailStr)

  val dobStr = "1970-01-01"
  val dob: LocalDate = LocalDate.parse(dobStr)
  val itmpName = ItmpName(Some(firstName), Some(lastName), Some(lastName))
  val itmpDob: Option[LocalDate] = Some(LocalDate.parse(dobStr))

  val line1 = "Casterly Rock"
  val line2 = "The Westerlands"
  val line3 = "Westeros"
  val postCode = "BA148FY"
  val countryCode = "GB"
  val itmpAddress = ItmpAddress(Some(line1), Some(line2), Some(line3), None, None, Some(postCode), Some(countryCode), Some(countryCode))

  val nsiUserInfo = NSIUserInfo(
    firstName, lastName, toJavaDate(dob), nino, ContactDetails(
    line1, line2, Some(line3), None, None, postCode, Some(countryCode), emailStr
  )
  )

  val mockedNINORetrieval = Enrolments(Set(enrolment))

  val mockedRetrievals: ~[~[~[~[~[~[~[Name, Option[String]], Option[LocalDate]], ItmpName], Option[LocalDate]], ItmpAddress], Enrolments], Option[String]] =
    new ~(name, email) and Option(dob) and itmpName and itmpDob and itmpAddress and Enrolments(Set(enrolment)) and Option("internalId")

  val mockedRetrievalsMissingUserInfo: ~[~[~[~[~[~[~[Name, Option[String]], Option[LocalDate]], ItmpName], Option[LocalDate]], ItmpAddress], Enrolments], Option[String]] =
    new ~(Name(None, None), email) and Option(dob) and ItmpName(None, None, None) and itmpDob and itmpAddress and Enrolments(Set(enrolment))and Option("internalId")

  val mockedRetrievalsMissingNinoEnrolment: ~[~[~[~[~[~[~[Name, Option[String]], Option[LocalDate]], ItmpName], Option[LocalDate]], ItmpAddress], Enrolments], Option[String]] =
    new ~(name, email) and Option(dob) and itmpName and itmpDob and itmpAddress and Enrolments(Set())and Option("internalId")

  def mockAuthResultWithFail(ex: Throwable): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier, _: ExecutionContext))
      .expects(AuthProviders(GovernmentGateway), *, *, *)
      .returning(Future.failed(ex))

  def mockAuthWithRetrievalsWithFail(predicate: Predicate)(ex: Throwable): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Enrolments])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, *, *, *)
      .returning(Future.failed(ex))

  def mockAuthWithNINORetrievalWithSuccess(predicate: Predicate)(result: Enrolments) =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Enrolments])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, Retrievals.authorisedEnrolments, *, *)
      .returning(Future.successful(result))

  def mockAuthWithAllRetrievalsWithSuccess(predicate: Predicate)(result: UserRetrievalType) =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[UserRetrievalType])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, UserRetrievals and Retrievals.authorisedEnrolments and Retrievals.internalId, *, *)
      .returning(Future.successful(result))

  def mockAuthWithNoRetrievals(predicate: Predicate): Unit =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, EmptyRetrieval, *, *)
      .returning(Future.successful(EmptyRetrieval))

}
