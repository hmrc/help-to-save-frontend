/*
 * Copyright 2022 HM Revenue & Customs
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
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.helptosavefrontend.models.HtsAuth.UserInfoRetrievals
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload
import uk.gov.hmrc.helptosavefrontend.models.userinfo.NSIPayload.ContactDetails
import uk.gov.hmrc.helptosavefrontend.util.toJavaDate
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

object AuthSupport {

  implicit class ROps[A, B](val r: ~[A, B]) {
    def and[C](c: C): ~[~[A, B], C] = new ~(r, c)
  }

}

trait AuthSupport extends MockFactory {

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
  val mockedNINORetrieval: Option[String] = Some(nino)

  val mockedNINOAndNameRetrieval: ~[~[Option[Name], Option[ItmpName]], Option[String]] = new ~(
    Some(name),
    Some(itmpName)
  ) and mockedNINORetrieval

  val mockedNINOAndNameRetrievalMissingNino: ~[~[Option[Name], Option[ItmpName]], Option[String]] = new ~(
    Some(name),
    Some(itmpName)
  ) and None

  val mockedNINOAndNameRetrievalMissingName: ~[~[Option[Name], Option[ItmpName]], Option[String]] = new ~(
    Some(Name(None, None)),
    Some(ItmpName(None, None, None))
  ) and mockedNINORetrieval

  val mockedRetrievals
    : ~[~[~[~[~[~[Option[Name], Option[String]], Option[LocalDate]], Option[ItmpName]], Option[LocalDate]], Option[
      ItmpAddress
    ]], Option[String]] =
    new ~(Some(name), email) and Option(dob) and Some(itmpName) and itmpDob and Some(itmpAddress) and mockedNINORetrieval

  def mockedRetrievalsWithEmail(
    email: Option[String]
  ): ~[~[~[~[~[~[Option[Name], Option[String]], Option[LocalDate]], Option[ItmpName]], Option[LocalDate]], Option[
    ItmpAddress
  ]], Option[String]] =
    new ~(Some(name), email) and Option(dob) and Some(itmpName) and itmpDob and Some(itmpAddress) and mockedNINORetrieval

  val mockedRetrievalsMissingUserInfo
    : ~[~[~[~[~[~[Option[Name], Option[String]], Option[LocalDate]], Option[ItmpName]], Option[LocalDate]], Option[
      ItmpAddress
    ]], Option[String]] =
    new ~(Some(Name(None, None)), email) and Option(dob) and Some(ItmpName(None, None, None)) and itmpDob and Some(
      itmpAddress
    ) and mockedNINORetrieval

  val mockedRetrievalsMissingNinoEnrolment
    : ~[~[~[~[~[~[Option[Name], Option[String]], Option[LocalDate]], Option[ItmpName]], Option[LocalDate]], Option[
      ItmpAddress
    ]], Option[String]] =
    new ~(Some(name), email) and Option(dob) and Some(itmpName) and itmpDob and Some(itmpAddress) and None

  def mockAuthResultWithFail(ex: Throwable): Unit =
    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier, _: ExecutionContext))
      .expects(AuthProviders(GovernmentGateway), *, *, *)
      .returning(Future.failed(ex))

  def mockAuthWithRetrievalsWithFail(predicate: Predicate)(ex: Throwable): Unit =
    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[Enrolments])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, *, *, *)
      .returning(Future.failed(ex))

  def mockAuthWithNINORetrievalWithSuccess(predicate: Predicate)(result: Option[String]): Unit =
    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[Option[String]])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, v2.Retrievals.nino, *, *)
      .returning(Future.successful(result))

  def mockAuthWithNINOAndName(predicate: Predicate)(result: NameRetrievalType): Unit =
    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[NameRetrievalType])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, v2.Retrievals.name and v2.Retrievals.itmpName and v2.Retrievals.nino, *, *)
      .returning(Future.successful(result))

  def mockAuthWithAllRetrievalsWithSuccess(predicate: Predicate)(result: UserRetrievalType): Unit =
    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[UserRetrievalType])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, UserInfoRetrievals and v2.Retrievals.nino, *, *)
      .returning(Future.successful(result))

  def mockAuthWithNoRetrievals(predicate: Predicate): Unit =
    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[Unit])(_: HeaderCarrier, _: ExecutionContext))
      .expects(predicate, EmptyRetrieval, *, *)
      .returning(Future.successful(EmptyRetrieval))

}
