package uk.gov.hmrc.integration.cucumber.stepdefs.generic

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


class ShutdownTest extends BasePage{

  Then ("""^shutdown browser$""") { () =>
    ShutdownTest()
  }

}
