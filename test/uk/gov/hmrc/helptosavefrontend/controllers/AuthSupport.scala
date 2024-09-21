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

package uk.gov.hmrc.helptosavefrontend.controllers

import org.mockito.ArgumentMatchersSugar.*
import org.mockito.IdiomaticMockito
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.syntax.retrieved.authSyntaxForRetrieved
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.UserInfoRetrievals
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload.ContactDetails

import java.time.LocalDate
import scala.concurrent.Future

object AuthSupport {

  implicit class ROps[A, B](val r: ~[A, B]) {
    def and[C](c: C): ~[~[A, B], C] = new ~(r, c)
  }

}

trait AuthSupport extends IdiomaticMockito {

  import AuthSupport._

  type NameRetrievalType = ~[~[Option[Name], Option[ItmpName]], Option[String]]

  type UserRetrievalType =
    Option[Name] ~ Option[String] ~ Option[LocalDate] ~ Option[ItmpName] ~ Option[LocalDate] ~ Option[ItmpAddress] ~ Option[
      String
    ]

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  val nino = "WM123456C"

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
  val itmpAddress =
    ItmpAddress(Some(line1), Some(line2), Some(line3), None, None, Some(postCode), Some(countryCode), Some(countryCode))

  val nsiPayload = NSIPayload(
    firstName,
    lastName,
    dob,
    nino,
    ContactDetails(
      line1,
      line2,
      Some(line3),
      None,
      None,
      postCode,
      Some(countryCode),
      emailStr
    ),
    "online",
    None,
    "V2.0",
    "MDTP REGISTRATION"
  )

  val enrolmentsWithMatchingNino: Enrolments = Enrolments(
    Set(
      Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", nino)), "activated")
    )
  )

  val enrolmentsWithNoMatchingNino: Enrolments = Enrolments(
    Set(
      Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", "invalid-nino")), "activated")
    )
  )

  val noPersonalTaxEnrolment: Enrolments = Enrolments(
    Set(
      Enrolment("HMRC-PT", Seq(EnrolmentIdentifier("NINO", "invalid-nino")), "activated")
    )
  )

  val mockedNINORetrieval: Option[String] = Some(nino)
  val mockedNINORetrievalWithPTEnrolment: ~[Option[String], Enrolments] = Some(nino) and enrolmentsWithMatchingNino

  val mockedNINOAndNameRetrieval: ~[~[~[Option[Name], Option[ItmpName]], Option[String]], Enrolments] = new ~(
    Some(name),
    Some(itmpName)
  ) and mockedNINORetrieval and enrolmentsWithMatchingNino

  val mockedNINOAndNameRetrievalMissingNino
    : ~[~[~[Option[Name], Option[ItmpName]], Option[String]], Enrolments] = new ~(
    Some(name),
    Some(itmpName)
  ) and None and noPersonalTaxEnrolment

  val mockedNINOAndNameRetrievalMissingName
    : ~[~[~[Option[Name], Option[ItmpName]], Option[String]], Enrolments] = new ~(
    Some(Name(None, None)),
    Some(ItmpName(None, None, None))
  ) and mockedNINORetrieval and enrolmentsWithMatchingNino

  val mockedRetrievals
    : ~[~[~[~[~[~[~[Option[Name], Option[String]], Option[LocalDate]], Option[ItmpName]], Option[LocalDate]], Option[
      ItmpAddress
    ]], Option[String]], Enrolments] =
    new ~(Some(name), email) and Option(dob) and Some(itmpName) and itmpDob and Some(itmpAddress) and mockedNINORetrieval and enrolmentsWithMatchingNino

  def mockedRetrievalsWithEmail(
    email: Option[String]
  ): ~[~[~[~[~[~[Option[Name], Option[String]], Option[LocalDate]], Option[ItmpName]], Option[LocalDate]], Option[
    ItmpAddress
  ]], Option[String]] =
    new ~(Some(name), email) and Option(dob) and Some(itmpName) and itmpDob and Some(itmpAddress) and mockedNINORetrieval

  val mockedRetrievalsMissingUserInfo
    : ~[~[~[~[~[~[~[Option[Name], Option[String]], Option[LocalDate]], Option[ItmpName]], Option[LocalDate]], Option[
      ItmpAddress
    ]], Option[String]], Enrolments] =
    new ~(Some(Name(None, None)), email) and Option(dob) and Some(ItmpName(None, None, None)) and itmpDob and Some(
      itmpAddress
    ) and mockedNINORetrieval and enrolmentsWithMatchingNino

  val mockedRetrievalsMissingNinoEnrolment
    : ~[~[~[~[~[~[~[Option[Name], Option[String]], Option[LocalDate]], Option[ItmpName]], Option[LocalDate]], Option[
      ItmpAddress
    ]], Option[String]], Enrolments] =
    new ~(Some(name), email) and Option(dob) and Some(itmpName) and itmpDob and Some(itmpAddress) and None and noPersonalTaxEnrolment

  def mockAuthResultWithFail(ex: Throwable): Unit =
    mockAuthConnector.authorise(AuthProviders(GovernmentGateway), *)(*, *) returns Future.failed(ex)

  def mockAuthWithRetrievalsWithFail(predicate: Predicate)(ex: Throwable): Unit =
    mockAuthConnector.authorise(predicate, *)(*, *) returns Future.failed(ex)

  def mockAuthWithNINORetrievalWithSuccess(predicate: Predicate)(result: ~[Option[String], Enrolments]): Unit =
    mockAuthConnector.authorise(predicate, v2.Retrievals.nino and v2.Retrievals.allEnrolments)(*, *) returns Future
      .successful(result)

  def mockAuthWithNINOAndName(predicate: Predicate)(result: ~[NameRetrievalType, Enrolments]): Unit =
    mockAuthConnector
      .authorise(
        predicate,
        v2.Retrievals.name and v2.Retrievals.itmpName and v2.Retrievals.nino and v2.Retrievals.allEnrolments
      )(*, *) returns Future
      .successful(result)

  def mockAuthWithAllRetrievalsWithSuccess(predicate: Predicate)(result: ~[UserRetrievalType, Enrolments]): Unit =
    mockAuthConnector.authorise(predicate, UserInfoRetrievals and v2.Retrievals.nino and v2.Retrievals.allEnrolments)(
      *,
      *
    ) returns Future.successful(
      result
    )

  def mockAuthWithNoRetrievals(predicate: Predicate): Unit =
    mockAuthConnector.authorise(predicate, EmptyRetrieval)(*, *) returns Future.successful(EmptyRetrieval)

}
