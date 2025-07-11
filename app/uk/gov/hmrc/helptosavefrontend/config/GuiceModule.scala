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

package uk.gov.hmrc.helptosavefrontend.config

import com.google.inject.AbstractModule
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.frontend.filters.crypto.{SessionCookieCrypto, SessionCookieCryptoProvider}
import uk.gov.hmrc.play.bootstrap.http.HttpClientV2Provider

class GuiceModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[ServicesConfig]).toProvider(classOf[ConfigModule])
    bind(classOf[HttpClientV2]).toProvider(classOf[HttpClientV2Provider])
    bind(classOf[SessionCookieCrypto]).toProvider(classOf[SessionCookieCryptoProvider])
  }

}
