package uk.gov.hmrc.integration.cucumber.pages.summary

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

trait ProvidingFinancialServices extends BasePage {

  def verifyAdviceOrConsultancyService(answer: String): Assertion =
    answer match {
      case "Yes" => findById("companyProvidingFinancial.provides.advice.or.consultancyAnswer").getText shouldBe "Yes"
      case "No"  => findById("companyProvidingFinancial.provides.advice.or.consultancyAnswer").getText shouldBe "No"
    }


  def changeAdviceOrConsultancyService(): Unit = click on id("companyProvidingFinancial.provides.advice.or.consultancyChangeLink")

  def verifyIntermediary(answer: String): Assertion =
    answer match {
      case "Yes" => findById("companyProvidingFinancial.acts.as.intermediaryAnswer").getText shouldBe "Yes"
      case "No"  => findById("companyProvidingFinancial.acts.as.intermediaryAnswer").getText shouldBe "No"
    }

  def checkRowsHidden(answer: String): Assertion =
    answer match {
      case "Yes" => checkForText("Charge fees for introducing clients to financial service providers") shouldBe false
      case "No" => checkForText("Charge fees for introducing clients to financial service providers") shouldBe true
    }


  def changeIntermediary(): Unit = click on id("companyProvidingFinancial.acts.as.intermediaryChangeLink")

  def verifyChargeFees(answer: String): Assertion =
    answer match {
      case "Yes" => findById("companyProvidingFinancial.charges.feesAnswer").getText shouldBe "Yes"
      case "No"  => findById("companyProvidingFinancial.charges.feesAnswer").getText shouldBe "No"
    }

  def changeChargeFees(): Unit = click on id("companyProvidingFinancial.charges.feesChangeLink")

  def verifyAdditionalNonSecuritiesWork(answer: String): Assertion =
    answer match {
      case "Yes" => findById("companyProvidingFinancial.does.additional.work.when.introducing.clientAnswer").getText shouldBe "Yes"
      case "No"  => findById("companyProvidingFinancial.does.additional.work.when.introducing.clientAnswer").getText shouldBe "No"
    }

  def changeAdditionalNonSecuritiesWork(): Unit = click on id("companyProvidingFinancial.does.additional.work.when.introducing.clientChangeLink")

  def verifyDiscretionaryInvestmentManagementServices(answer: String): Assertion =
    answer match {
      case "Yes" => findById("companyProvidingFinancial.provides.discretionary.investment.managementAnswer").getText shouldBe "Yes"
      case "No"  => findById("companyProvidingFinancial.provides.discretionary.investment.managementAnswer").getText shouldBe "No"
    }

  def changeDiscretionaryInvestmentManagementServices(): Unit = click on id("companyProvidingFinancial.provides.discretionary.investment.managementChangeLink")

  def verifyLeasingVehiclesOrEquipments(answer: String): Assertion =
    answer match {
      case "Yes" => findById("companyProvidingFinancial.involved.in.leasing.vehicles.or.equipmentAnswer").getText shouldBe "Yes"
      case "No"  => findById("companyProvidingFinancial.involved.in.leasing.vehicles.or.equipmentAnswer").getText shouldBe "No"
    }

  def changeLeasingVehiclesOrEquipments(): Unit = click on id("companyProvidingFinancial.involved.in.leasing.vehicles.or.equipmentChangeLink")

  def verifyInvestmentFundManagementService(answer: String): Assertion =
    answer match {
      case "Yes" => findById("companyProvidingFinancial.provides.investment.fund.managementAnswer").getText shouldBe "Yes"
      case "No"  => findById("companyProvidingFinancial.provides.investment.fund.managementAnswer").getText shouldBe "No"
    }

  def changeInvestmentFundManagementService(): Unit = click on id("companyProvidingFinancial.provides.investment.fund.managementChangeLink")

  def verifyManageAdditionalFunds(answer: String): Assertion =
    answer match {
      case "Yes" => findById("companyProvidingFinancial.manages.funds.not.included.in.this.listAnswer").getText shouldBe "Yes"
      case "No"  => findById("companyProvidingFinancial.manages.funds.not.included.in.this.listAnswer").getText shouldBe "No"
    }

  def changeManageAdditionalFunds(): Unit = click on id("companyProvidingFinancial.manages.funds.not.included.in.this.listChangeLink")



}
