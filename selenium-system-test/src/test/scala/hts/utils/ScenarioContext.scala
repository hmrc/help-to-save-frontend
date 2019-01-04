/*
 * Copyright 2019 HM Revenue & Customs
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

package hts.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cucumber.api.DataTable
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.helptosavefrontend.models.userinfo.Address

import scala.collection.JavaConverters._
import scala.util.Random

object ScenarioContext extends NINOGenerator {

  private var dataTable: Option[Map[String, String]] = None

  def setDataTable(table: DataTable, nino: String): Unit = {
    val data = table.asMap(classOf[String], classOf[String]).asScala.updated("NINO", nino)
    dataTable = Some(Map(data.toList: _*))
  }

  def userInfo(): Either[String, TestUserInfo] = dataTable.fold[Either[String, TestUserInfo]](
    Left("No data table found")
  ){ table ⇒
      val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
      val info: TestUserInfo = TestUserInfo(
        table.get("first name"),
        table.get("last name"),
        table.get("NINO"),
        table.get("date of birth").map(s ⇒ LocalDate.parse(s, dateFormatter)),
        table.get("email address"),
        Address(
          List(
            table.get("address line 1"),
            table.get("address line 2"),
            table.get("address line 3"),
            table.get("address line 4"),
            table.get("address line 5")
          ).collect{ case Some(s) ⇒ s },
          table.get("postcode"),
          table.get("country code")
        ),
        TestBankDetails(
          table.get("bank account name"),
          table.get("bank account number"),
          table.get("bank sort code"),
          table.get("building society roll number")
        )
      )
      Right(info)
    }

  override def reset(): Unit = {
    super.reset()
    dataTable = None
  }

}

private[utils] trait NINOGenerator {

  private var generator = new Generator()

  private var current = generator.nextNino.value

  private def generateNINO(): String = {
    current = generator.nextNino.value
    current
  }

  private def toEligible(nino: String) = "AE" + nino.drop(2)

  protected def reset(): Unit = {
    generator = new Generator()
    current = generator.nextNino.value
  }

  def generateEligibleNINO(): String = {
    current = toEligible(generateNINO())
    current
  }

  def generateIneligiblePrefix(): String = {
    val prefixes = Set("WP0000", "WP0010", "WP1000", "WP1010")
    val rnd = new Random
    prefixes.toVector(rnd.nextInt(prefixes.size))
  }

  def generateHTTPErrorCodePrefix(code: Int): String = {
    val codePrefix = code.toString
    "ES" + codePrefix
  }

  def generateIneligibleNINO(): String = {
    val ineligibleNino = generateIneligiblePrefix() + generateNINO().drop(6)
    current = ineligibleNino
    ineligibleNino
  }

  def generateHTTPErrorCodeNINO(code: Int): String = {
    val HTTPErrorCodeNino = generateHTTPErrorCodePrefix(code) + generateNINO().drop(5)
    current = HTTPErrorCodeNino
    HTTPErrorCodeNino
  }

  def currentNINO(): String = current

  def defineNINO(nino: String): Unit = {
    current = nino
  }
}
