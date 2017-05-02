package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

object BusinessActivityDescriptionPage extends BasePage {
  // ToDo Check the urls and labels once the page has been completed

  override val url: String =s"$basePageUrl/business-activity-description"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("bad.body.header"))

  def businessDescriptionField: TextArea = textArea("description")

  var businessDescription: String = ""

  def initialise() = {
    businessDescription = "Testing our business description here"
  }

  def provideBusinessDescription(description: String) = description match {
    case "valid"      =>  businessDescriptionField.value = "Testing our business description here"
                           businessDescription = "Testing our business description here"
    case "invalid"    =>  businessDescriptionField.value = "£$£%^^"
    case "different"  =>  businessDescriptionField.value = "Different description for my company"
                           businessDescription = "Different description for my company"
  }

//  def provideDifferentBusinessDescription() = {
//    businessDescriptionField.value = "This is a different description for my company"
//
//    businessDescription = "This is a different description for my company"
//  }

  def checkBusinessDescription() = businessDescriptionField.value shouldBe businessDescription

  def checkEmptyDescriptionError() = validateErrorMessages("description", "error.businessActivityDescription.empty")

  def checkInvalidDescriptionError() = validateErrorMessages("description", "bad.businessDescription.invalid")
}
