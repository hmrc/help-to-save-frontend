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

package hts.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cucumber.api.DataTable
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.helptosavefrontend.models.userinfo.Address

import scala.collection.JavaConverters._
import scala.util.Random

object ScenarioContext extends NINOGenerator {

  private var dataTable: Option[DataTable] = None

  def setDataTable(table: DataTable): Unit = dataTable = Some(table)

  //  def removeField(field: String, table: DataTable): Unit = dataTable = {
  //    val newTable = table.diff(table.diffableRows())
  //    Some(newTable)
  //  }

  def userInfo(): Either[String, TestUserInfo] = dataTable.fold[Either[String, TestUserInfo]](
    Left("No data table found")
  ){ table ⇒
      val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

      val info: TestUserInfo = TestUserInfo(
        getField(table)("first name"),
        getField(table)("last name"),
        getField(table)("NINO"),
        getField(table)("date of birth").map(s ⇒ LocalDate.parse(s, dateFormatter)),
        getField(table)("email address"),
        Address(
          List(
            getField(table)("address line 1"),
            getField(table)("address line 2"),
            getField(table)("address line 3"),
            getField(table)("address line 4"),
            getField(table)("address line 5")
          ).collect{ case Some(s) ⇒ s },
          getField(table)("postcode"),
          getField(table)("country code")
        ))

      Right(info)
    }

  override def reset(): Unit = {
    super.reset()
    dataTable = None
  }

  private def getField(table: DataTable)(name: String): Option[String] = {
    val data = table.asMap(classOf[String], classOf[String]).asScala
    val value = data.get(name) match {
      case Some(f) if f.equals("<eligible>") ⇒ generateEligibleNINO
      case Some(x)                           ⇒ x
      case None                              ⇒ ""
    }
    Some(value)
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

  //Private Beta
  def generateIneligiblePrefix(): String = {
    val prefixes = Set("NE02", "NE03")
    val rnd = new Random
    prefixes.toVector(rnd.nextInt(prefixes.size))
  }

  def generateIneligibleNINO(): String = {
    val ineligibleNino = generateIneligiblePrefix() + generateNINO().drop(4)
    current = ineligibleNino
    ineligibleNino
  }

  def currentNINO(): String = current

}
