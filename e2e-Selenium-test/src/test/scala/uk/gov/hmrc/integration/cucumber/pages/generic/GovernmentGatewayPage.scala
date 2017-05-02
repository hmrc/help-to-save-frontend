package uk.gov.hmrc.integration.cucumber.pages.generic

//import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.BusinessActivityDescriptionPage
//import uk.gov.hmrc.integration.cucumber.pages.vatFinancials.vatBankAccount.CompanyBankDetailsPage
//import uk.gov.hmrc.integration.cucumber.pages.vatTradingDetails.vatChoice.StartDatePage
//import uk.gov.hmrc.integration.cucumber.pages.vatFinancials.{EstimateVatTurnoverPage, EstimateZeroRatedSalesPage}


object GovernmentGatewayPage extends BasePage{

  def checkBodyTitle(): Boolean = checkBodyTitle("gg.body.header")

  override val url: String = s"$basePageUrl"
  def navigateTo(redirectUrl: String): Unit = goTo(s"$basePageUrl/account/sing-in?continue=$redirectUrl")
  def submit(): Unit = clickOn(cssSelector("button.button"))

  def userId: TextField = textField("userId")
  def password: PasswordField = pwdField("password")

  def signIn(id: String) = {
    id match {
      case "authenticated"   => userId.value = "857584344120" // "929903997183"
                                password.value = "p2ssword"
                                submit()
      //                          goToCurrentProfileSetup()

      case "unauthenticated" => userId.value = ""
                                password.value = ""
                                submit()
    }
  }
}
