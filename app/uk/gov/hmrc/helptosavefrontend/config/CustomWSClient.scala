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

import java.io.{File, FileInputStream, FileOutputStream}
import java.security.{KeyStore, SecureRandom}
import java.time.LocalDate
import java.util.Base64
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{nsiAuthHeaderKey, nsiBasicAuth, nsiUrl}
import com.typesafe.config.ConfigFactory
import io.netty.handler.ssl.SslContextBuilder
import net.ceedubs.ficus.Ficus._
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import play.api.http.Writeable
import play.api.libs.json.{Format, Json}
import play.api.{Logger, Play}
import play.api.libs.ws.ahc.AhcWSClient
import play.api.mvc.Codec
import uk.gov.hmrc.helptosavefrontend.config.FrontendAppConfig.{nsiAuthHeaderKey, nsiUrl}
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo
import uk.gov.hmrc.helptosavefrontend.models.NSIUserInfo.ContactDetails

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try


/**
  * Created by suresh on 11/07/17.
  */
object CustomWSClient {

  protected val sslContext = {
    Logger.info("initialising key/trust store")
    val config = ConfigFactory.load()
    val maybeKeyStoreData = config.as[Option[String]]("play.ws.ssl.keyManager.stores.data")
    val maybeKeyStorePassword = config.as[Option[String]]("play.ws.ssl.keyManager.stores.password")
    val maybeKeyStoreType = config.as[Option[String]]("play.ws.ssl.keyManager.stores.type")

    (maybeKeyStoreData, maybeKeyStorePassword, maybeKeyStoreType) match {
      case (Some(keyStoreData), Some(keyStorePassword), Some(keyStoreType)) =>

        val result = for {
          dataBytes ← Try(Base64.getDecoder.decode(keyStoreData))
          file ← writeToTempFile(dataBytes)
        } yield file

        val keyStoreStream = new FileInputStream(result.get)
        val ks = KeyStore.getInstance(keyStoreType)

        val decryptedPass = new String(Base64.getDecoder.decode(keyStorePassword)).toCharArray

        val kmf = KeyManagerFactory.getInstance("SunX509")
        kmf.init(ks, decryptedPass)

        val keyManagers = kmf.getKeyManagers
        val tmf = TrustManagerFactory
          .getInstance(TrustManagerFactory.getDefaultAlgorithm)
        // Using null here initialises the TMF with the default trust store.
        tmf.init(null: KeyStore)
        val secureRandom = new SecureRandom()

        val javaSslContext = SSLContext.getInstance("TLS")
        javaSslContext.init(keyManagers, tmf.getTrustManagers, secureRandom)

        SslContextBuilder.forClient()
          .keyManager(kmf)
          .trustManager(tmf)
          .build()
      case _ =>
        null
    }
  }

  def writeToTempFile(data: Array[Byte]): Try[File] = Try {
    val file = File.createTempFile(getClass.getSimpleName, ".tmp")
    file.deleteOnExit()
    val os = new FileOutputStream(file)
    os.write(data)
    os.flush()
    os.close()
    file
  }

  def apply(): AhcWSClient = {

    val contact = ContactDetails(List("sss", "dddd", "aaaa"), "post", Some("GB"), "test@email.com")
    implicit val materializer = Play.current.materializer
    implicit val nsiUserInfoFormat: Format[NSIUserInfo] = Json.format[NSIUserInfo]

    val config = new DefaultAsyncHttpClientConfig.Builder().setSslContext(sslContext).build()

    //    val response = AhcWSClient(config).url(FrontendAppConfig.nsiUrl).withHeaders(nsiAuthHeaderKey → nsiBasicAuth)
    //      .post(Json.toJson(NSIUserInfo("sureshForename", "sureshSurname", LocalDate.now().minusYears(30), "AE123456C", contact)).toString())
    //
    //    response.map(wsResponse => Logger.info(s"response is xxxx = ${wsResponse.body}"))

    AhcWSClient(config)
  }

}
