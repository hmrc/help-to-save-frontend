package uk.gov.hmrc.integration.cucumber.pages.summary

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice.StartDatePage


trait VatDetails extends BasePage {

  def changeTaxableTurnover(): Unit = click on id("vatDetails.taxableTurnoverChangeLink")

  def verifyTaxableTurnover(answer: String): Assertion =
    answer match {
      case "Yes" => findById("vatDetails.taxableTurnoverAnswer").getText shouldBe "Yes"
      case "No"  => findById("vatDetails.taxableTurnoverAnswer").getText shouldBe "No"
    }

  def changeRegisterVoluntarily(): Unit = click on id("vatDetails.necessityChangeLink")

  def verifyRegisterVoluntarilyYes(): Unit = findById("vatDetails.necessityAnswer").getText shouldBe "Yes"

  def verifyRegisterVoluntarilyNo(): Unit = findById("vatDetails.necessityAnswer").getText shouldBe "No"

  def verifyVoluntaryRegistrationReason(reason: String): Assertion =
    reason match {
      case "the company already sells..."   => findById("vatDetails.voluntaryRegistrationReasonAnswer").getText shouldBe "The company already sells taxable goods or services"
      case "the company intends to sell..." => findById("vatDetails.voluntaryRegistrationReasonAnswer").getText shouldBe "The company intends to sell taxable goods or services in the future"
    }


  def changeVoluntaryRegistrationReason(): Unit = click on id("vatDetails.voluntaryRegistrationReasonChangeLink")

  def changeVatStartDate(): Unit = click on id("vatDetails.startDateChangeLink")

  def verifyChangeStartDate(): Assertion =  findById("vatDetails.startDateChangeLink") shouldBe 'displayed

  def verifyVatStartDate(): Unit = findById("vatDetails.startDateAnswer").getText shouldBe StartDatePage.startDate

}
