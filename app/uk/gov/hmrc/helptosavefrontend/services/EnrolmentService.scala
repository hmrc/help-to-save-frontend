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

package uk.gov.hmrc.helptosavefrontend.services

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.helptosavefrontend.connectors.ITMPConnector
import uk.gov.hmrc.helptosavefrontend.repo.EnrolmentStore
import uk.gov.hmrc.helptosavefrontend.repo.EnrolmentStore.Status
import uk.gov.hmrc.helptosavefrontend.util.{NINO, Result}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EnrolmentServiceImpl])
trait EnrolmentService {

  def enrolUser(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def setITMPFlag(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def getUserEnrolmentStatus(nino: NINO)(implicit ec: ExecutionContext): Result[Status]

}

@Singleton
class EnrolmentServiceImpl @Inject()(enrolmentStore: EnrolmentStore,
                                     itmpConnector: ITMPConnector) extends EnrolmentService {

  def enrolUser(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    for {
      _ ← enrolmentStore.update(nino, itmpFlag = false)
      _ ← setITMPFlagAndUpdateMongo(nino)
    } yield ()

  def setITMPFlag(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    setITMPFlagAndUpdateMongo(nino)

  def getUserEnrolmentStatus(nino: NINO)(implicit ec: ExecutionContext): Result[Status] =
    enrolmentStore.get(nino)


  private def setITMPFlagAndUpdateMongo(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, Unit] = for {
    _ ← itmpConnector.setFlag(nino)(hc, ec)
    _ ← enrolmentStore.update(nino, itmpFlag = true)
  } yield ()

}
