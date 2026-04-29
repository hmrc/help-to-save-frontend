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

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{MessagesControllerComponents, Request, RequestHeader, Result}
import uk.gov.hmrc.helptosavefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavefrontend.util.MaintenanceSchedule
import uk.gov.hmrc.helptosavefrontend.util.{Result => EitherTResult}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CustomBaseController @Inject() (
  cpd: CommonPlayDependencies,
  mcc: MessagesControllerComponents,
  errorHandler: ErrorHandler,
  maintenanceSchedule: MaintenanceSchedule
) extends FrontendController(mcc) with I18nSupport {

  override implicit val messagesApi: MessagesApi = cpd.messagesApi

  implicit val appConfig: FrontendAppConfig = cpd.appConfig

  implicit val maintenence: MaintenanceSchedule = maintenanceSchedule

  val Messages: MessagesApi = messagesApi

  implicit override def hc(implicit rh: RequestHeader): HeaderCarrier =
    HeaderCarrierConverter.fromRequestAndSession(rh, rh.session)

  def internalServerError()(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
    errorHandler.internalServerErrorTemplate(request).map(InternalServerError(_))

  protected def internalServerErrorResultT(implicit request: Request[_], ec: ExecutionContext): EitherTResult[Result] =
    EitherT.liftF(internalServerError())

  protected def foldWithInternalServerError[A](
    result: EitherTResult[A]
  )(
    onError: String => Unit
  )(
    onSuccess: A => Future[Result]
  )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
    result.foldF(
      e => {
        onError(e)
        internalServerError()
      },
      onSuccess
    )

  protected def mergeWithInternalServerError(
    result: EitherTResult[Result]
  )(
    onError: String => Unit
  )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
    foldWithInternalServerError(result)(onError)(Future.successful)
}

class CommonPlayDependencies @Inject() (val appConfig: FrontendAppConfig, val messagesApi: MessagesApi)
