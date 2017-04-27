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

package uk.gov.hmrc.helptosavefrontend

import cats.data.EitherT
import play.api.libs.json.{JsError, Reads}
import play.mvc.Http
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.helptosavefrontend.util.JsErrorOps._
import scala.concurrent.{ExecutionContext, Future}

package object util {

  type NINO = String

  type Result[A] = EitherT[Future, String, A]

  /**
    * Perform a GET request to the given URL then validate the resulting JSON to type `A`. If validation is not
    * possible, return an error. If the GET request does not come back with a HTTP status 200, return an error
    */
  def getResult[A](url: String)(implicit reads: Reads[A], hc: HeaderCarrier, ec: ExecutionContext): Result[A] =
    EitherT[Future, String, A](
      WSHttp.GET(url).map { response ⇒
        val status = response.status
        if (status == Http.Status.OK) {
          response.json.validate[A].fold(
            errors ⇒ Left(s"Could not parse response from $url: ${JsError(errors).prettyPrint()}"),
            Right(_)
          )
        } else {
          // response didn't come back OK - something went wrong
          Left(s"Could not obtain result from $url - [HTTP status $status, body: '${response.body}'")
        }
      })

}
