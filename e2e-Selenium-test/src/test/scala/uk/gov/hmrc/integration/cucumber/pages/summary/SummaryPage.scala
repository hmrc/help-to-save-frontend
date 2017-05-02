package uk.gov.hmrc.integration.cucumber.pages.summary

object SummaryPage extends VatDetails
  with CompanyDetails
  with CompanyContactDetails
  with ProvidingFinancialServices{

  override val url: String = s"$basePageUrl/summary"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("summary.body.header"))
}
