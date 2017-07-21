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

import javax.net.ssl._

import play.api.Logger
import play.server.api.SSLEngineProvider

class CustomSSLEngineProvider extends SSLEngineProvider {

  HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier {
    override def verify(host: String, sslSession: SSLSession): Boolean = {
      Logger.info(s"hostname is $host")
      Logger.info(s"sslSession chain is ${sslSession.getPeerCertificateChain}")
      Logger.info(s"sslSession getPeerHost ${sslSession.getPeerHost}")
      true
    }
  })


  override def createSSLEngine: SSLEngine = {

    Logger.info(s"invoking CustomSSLEngineProvider")

    val sslEngine: SSLEngine = SSLContext.getDefault.createSSLEngine

    val sslParams = new SSLParameters
    sslParams.setEndpointIdentificationAlgorithm("HTTPS")
    sslEngine.setSSLParameters(sslParams)
    sslEngine
  }
}
