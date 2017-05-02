package uk.gov.hmrc.integration.cucumber.pages.summary

import org.scalatest.Assertion
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.vatContact.vatDigitalContact.BusinessContactDetailsPage

trait CompanyContactDetails extends BasePage{

  def verifyCompanyContactDetails(): Assertion = {
    findById("companyContactDetails.emailAnswer").getText shouldBe BusinessContactDetailsPage.emailAddress
    findById("companyContactDetails.daytimePhoneAnswer").getText shouldBe BusinessContactDetailsPage.daytimePhoneNo
    findById("companyContactDetails.mobileAnswer").getText shouldBe BusinessContactDetailsPage.mobileNo
    findById("companyContactDetails.websiteAnswer").getText shouldBe BusinessContactDetailsPage.webAddress
  }

  def verifyChangedBusinessEmailAddress(): Assertion = {
    findById("companyContactDetails.emailAnswer").getText shouldBe BusinessContactDetailsPage.emailAddress
  }

  def verifyChangedBusinessDaytimePhone(): Assertion = {
    findById("companyContactDetails.daytimePhoneAnswer").getText shouldBe BusinessContactDetailsPage.daytimePhoneNo
  }

  def verifyChangedBusinessMobileNumber(): Assertion = {
    findById("companyContactDetails.mobileAnswer").getText shouldBe BusinessContactDetailsPage.mobileNo
  }

  def verifyChangedWebAddress(): Assertion = {
    findById("companyContactDetails.websiteAnswer").getText shouldBe BusinessContactDetailsPage.webAddress
  }

  def changeBuisnessEmailAddress() = click on id("companyContactDetails.emailChangeLink")

  def changeBusinessDaytimePhone() = click on id("companyContactDetails.daytimePhoneChangeLink")

  def changeBusinessMobileNumber() = click on id("companyContactDetails.mobileChangeLink")

  def changeBusinessWebsiteAddress() = click on id("companyContactDetails.websiteChangeLink")

  def clickChangeCompanyContactOption(option: String): Unit = option match {
    case "Business email address" => click on id("companyContactDetails.emailChangeLink")
    case "Business daytime phone number" => click on id("companyContactDetails.daytimePhoneChangeLink")
    case "Business mobile number" => click on id("companyContactDetails.mobileChangeLink")
    case "Business website address" => click on id("companyContactDetails.websiteChangeLink")
  }

  def checkPrePopulateCompanyContactOption(option: String): Assertion = option match {
    case "Business email address" => findById("companyContactDetails.emailAnswer").getText shouldBe BusinessContactDetailsPage.emailAddress
    case "Business daytime phone number" => findById("companyContactDetails.daytimePhoneAnswer").getText shouldBe BusinessContactDetailsPage.daytimePhoneNo
    case "Business mobile number" => findById("companyContactDetails.daytimePhoneAnswer").getText shouldBe BusinessContactDetailsPage.daytimePhoneNo
    case "Business website address" => findById("companyContactDetails.mobileAnswer").getText shouldBe BusinessContactDetailsPage.mobileNo
  }

  def verifyUpdatedCompanyContactOption(option: String): Unit = option match{
    case "Business email address" => findById("companyContactDetails.emailAnswer").getText shouldBe BusinessContactDetailsPage.emailAddress
    case "Business daytime phone number" => findById("companyContactDetails.daytimePhoneAnswer").getText shouldBe BusinessContactDetailsPage.daytimePhoneNo
    case "Business mobile number" => findById("companyContactDetails.mobileAnswer").getText shouldBe BusinessContactDetailsPage.mobileNo
    case "Business website address" => findById("companyContactDetails.websiteAnswer").getText shouldBe BusinessContactDetailsPage.webAddress
  }

  def checkBusinessEmailAddress(): Assertion = findById("companyContactDetails.emailAnswer").getText shouldBe BusinessContactDetailsPage.emailAddress

  def checkBusinessDayPhoneNo(): Assertion = findById("companyContactDetails.daytimePhoneAnswer").getText shouldBe BusinessContactDetailsPage.daytimePhoneNo

  def checkBusinessMobileNo(): Assertion = findById("companyContactDetails.mobileAnswer").getText shouldBe BusinessContactDetailsPage.mobileNo

}
