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

package uk.gov.hmrc.helptosavefrontend.config

import akka.stream.Materializer
import configs.syntax._
import play.api.Configuration
import play.api.mvc.{Call, RequestHeader, Result, Results}
import uk.gov.hmrc.helptosavefrontend.util.Logging
import uk.gov.hmrc.whitelist.AkamaiWhitelistFilter

import scala.concurrent.Future

class WhitelistFilter(configuration: Configuration, materializer: Materializer) extends AkamaiWhitelistFilter with Logging {

  override def whitelist: Seq[String] =
    configuration.underlying.get[List[String]]("http-header-ip-whitelist").value


  // This is the `Call` used in the `Redirect` when an IP is present in the header
  // of the HTTP request but is not in the whitelist
  override def destination: Call = Call("GET", "http://not-allowed")

  override implicit def mat: Materializer = materializer

  // if there is no IP address in the header return a `Forbidden` status
  override def noHeaderAction(f:  (RequestHeader) ⇒ Future[Result],
                              rh: RequestHeader): Future[Result] = {
    logger.info("Whitelisting enabled but no IP found in header")
    Future.successful(Results.Forbidden)
  }

}
