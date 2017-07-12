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
import java.util
import java.util.Base64
import javax.inject.Singleton
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import com.google.inject.Inject
import com.typesafe.config.{ConfigObject, ConfigValueFactory}
import io.netty.handler.ssl.SslContextBuilder
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import play.api._
import play.api.libs.ws.WSConfigParser
import play.api.libs.ws.ahc.AhcWSClient

import scala.util.Try


/**
  * Created by suresh on 11/07/17.
  */
@Singleton
class CustomWSClient @Inject()(implicit app: Application) {

  implicit val configuration = app.configuration

  implicit val env = Environment(app.path, app.classloader, app.mode)

  val sslContext = {

    Logger.info("initialising key/trust store")

    val mergedConfiguration = mergeAllStores(configuration)
    val internalParser = new WSConfigParser(mergedConfiguration, env)
    val config = internalParser.parse()

    val ksConfig = config.ssl.keyManagerConfig.keyStoreConfigs.head

    val result = for {
      dataBytes ← Try(Base64.getDecoder.decode(ksConfig.data.get))
      file ← writeToTempFile(dataBytes)
    } yield file

    Logger.info(s"CustomWSClient wrote keystore to file: ${result.get.getAbsolutePath}")

    val keyStoreStream = new FileInputStream(result.get)
    val ks = KeyStore.getInstance("jks")

    val decryptedPass = new String(Base64.getDecoder.decode(ksConfig.password.get)).toCharArray

    ks.load(keyStoreStream, decryptedPass)

    val kmf = KeyManagerFactory.getInstance("SunX509")
    kmf.init(ks, decryptedPass)

    val keyManagers = kmf.getKeyManagers
    val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    // Using null here initialises the TMF with the default trust store.
    tmf.init(null: KeyStore)
    val secureRandom = new SecureRandom()

    val javaSslContext = SSLContext.getInstance("TLSv1.2")
    javaSslContext.init(keyManagers, tmf.getTrustManagers, secureRandom)

    SslContextBuilder.forClient()
      .keyManager(kmf)
      .trustManager(tmf)
      .build()
  }

  private def mergeAllStores(config: Configuration): Configuration = {
    mergeStores(mergeStores(config, "key"), "trust")
  }

  private def mergeStores(config: Configuration, name: String): Configuration = {
    val under = config.underlying
    if (under.hasPath(s"play.ws.ssl.${name}Manager.store")) {
      val singleStore: ConfigObject = under.getObject(s"play.ws.ssl.${name}Manager.store")
      val stores: util.List[AnyRef] = under.getList(s"play.ws.ssl.${name}Manager.stores").unwrapped()
      if (singleStore != null) {
        stores.add(singleStore)
      }
      config.copy(underlying = config.underlying.withValue(s"play.ws.ssl.${name}Manager.stores", ConfigValueFactory.fromIterable(stores)))
    } else config
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

  def getCLient() = {
    implicit val materializer = Play.current.materializer

    val config = new DefaultAsyncHttpClientConfig.Builder().setSslContext(sslContext).build()

    AhcWSClient(config)
  }

}
