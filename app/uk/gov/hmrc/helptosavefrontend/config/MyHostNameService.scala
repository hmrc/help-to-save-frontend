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

import java.net.{InetAddress, UnknownHostException}

import org.apache.commons.lang3.reflect.FieldUtils.readStaticField
import play.api.Logger
import sun.net.spi.nameservice.NameService

import scala.util.Try

object MyHostNameService {
  // Force to load fake hostname resolution for tests to pass
  Try {
    val nameServices: List[NameService] = readStaticField(classOf[InetAddress], "nameServices", true).asInstanceOf[List[NameService]]
    new MyHostNameService() :: nameServices
  }.recover {
    case e => e.printStackTrace()
  }

}

class MyHostNameService extends NameService {

  override def lookupAllHostAddr(paramString: String): Array[InetAddress] = {

    Logger.info(s"coming here $paramString")

    if ("api.nsi.hts.esit" == paramString || "api.nsi.hts.esit.domain.tld" == paramString) {
      val arrayOfByte = sun.net.util.IPAddressUtil.textToNumericFormatV4("212.250.135.50")
      val address = InetAddress.getByAddress(paramString, arrayOfByte)
      Array[InetAddress](address)
    }
    else {
      throw new UnknownHostException(s"host $paramString is unknown")
    }
  }

  override def getHostByAddr(paramArrayOfByte: Array[Byte]) =
    throw new UnknownHostException(s"paramArrayOfByte ${new String(paramArrayOfByte)} is unknown")
}



