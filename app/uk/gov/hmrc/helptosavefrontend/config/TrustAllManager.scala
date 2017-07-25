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

import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl._

import play.api.Logger

object TrustAllManager {

  import javax.net.ssl.SSLSession

  // Create a trust manager that does not validate certificate chains
  val trustAllCerts: Array[TrustManager] = Array[TrustManager](new X509TrustManager {
    override def checkServerTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {
      Logger.info("All Servers trusted")
    }

    override def checkClientTrusted(x509Certificates: Array[X509Certificate], s: String): Unit = {
      Logger.info("All Servers trusted")
    }

    override def getAcceptedIssuers: Array[X509Certificate] = {
      Array.empty
    }
  })

  // Install the all-trusting trust manager
  val sc: SSLContext = SSLContext.getInstance("SSL")
  sc.init(Array.empty, trustAllCerts, new SecureRandom())
  HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory)

  // Create all-trusting host name verifier
  val allHostsValid = new HostnameVerifier {
    override def verify(host: String, sslSession: SSLSession): Boolean = {
      Logger.info("verification done for host $host")
      true
    }
  }

  // Install the all-trusting host verifier
  HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid)

  Logger.info("done initialising TrustAllManager")
}

