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

import java.io.FileInputStream
import java.security.KeyStore

import org.scalatest.FunSuite

/**
  * Created by jackie on 24/05/17.
  */
class SSLTest extends FunSuite {

  //val keyStore = KeyStore.getInstance("PKCS12")

  //val certKeyStoreLocation = new FileInputStream(new File("src/test/resources/keystores/ca-chain.cert.jks"))
  //val trustStore = KeyStore.getInstance("jks")
  // trustStore.load(certKeyStoreLocation, "changeit".toCharArray())


  //val clientAuthFactory = new org.apache.http.conn.ssl.SSLSocketFactory(keyStore, "changeit", trustStore)
  //clientAuthFactory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER)

  //val config = new SSLConfig().with().sslSocketFactory(clientAuthFactory).and().allowAllHostnames()


  test("check there is a NSI certificate in keystore") {
    val ks = KeyStore.getInstance(KeyStore.getDefaultType())
    val fis: FileInputStream = new FileInputStream("/usr/lib/jvm/java-8-oracle/jre/lib/security/cacerts")
    ks.load(fis, "changeit".toCharArray)
    assert(ks.containsAlias("vdc.tools.tax.service.gov.uk"))
  }

  test("hit the nsi end point to generate a stack trace") {
    val url = ""
  }

}
