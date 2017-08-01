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
import com.google.inject.Provider
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import play.api.{Configuration, Environment}
import play.api.inject.{ApplicationLifecycle, Binding, Module}
import play.api.libs.ws.{WSAPI, WSClient}
import play.api.libs.ws.ahc.AhcWSClient
import javax.inject.{Inject, Singleton}

import uk.gov.hmrc.helptosavefrontend.util.Logging

import scala.concurrent.Future

@Singleton
class AcceptAllAhcWSAPI @Inject()(lifecycle: ApplicationLifecycle, configuration: Configuration)(implicit materializer: Materializer)
  extends WSAPI with Logging {

  logger.info("Starting up AcceptAllAhcWSAPI")

  private val context = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
  private val builder = new DefaultAsyncHttpClientConfig.Builder

  private val config = builder.setAcceptAnyCertificate(true).setSslContext(context).build()

  lazy val client = {
    val innerClient = new AhcWSClient(config)

    lifecycle.addStopHook { () =>
      Future.successful(innerClient.close())
    }

    innerClient
  }

  def url(url: String) = client.url(url)

}


class AcceptAllWSClientProvider @Inject()(wsApi: WSAPI) extends Provider[WSClient] {

  override def get(): WSClient = wsApi.client

}

class AcceptAllAhcWSModule extends Module {

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] = Seq(
    bind[WSAPI].to[AcceptAllAhcWSAPI],
    bind[WSClient].toProvider[AcceptAllWSClientProvider].in[Singleton]
  )

}
