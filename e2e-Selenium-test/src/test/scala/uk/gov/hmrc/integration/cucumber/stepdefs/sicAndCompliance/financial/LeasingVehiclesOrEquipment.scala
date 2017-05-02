package uk.gov.hmrc.integration.cucumber.stepdefs.sicAndCompliance.financial

import cucumber.api.scala.{EN, ScalaDsl}
import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage
import uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial.LeasingVehiclesOrEquipmentPage

class LeasingVehiclesOrEquipment extends ScalaDsl with EN with BasePage{

  When("""^I am presented with the leasing vehicles or equipments page$"""){ () =>
    go to LeasingVehiclesOrEquipmentPage
    LeasingVehiclesOrEquipmentPage.checkBodyTitle()
  }

  And("""^I select '(.*)' on the leasing vehicles or equipment page$"""){ (radioOption: String) =>
    LeasingVehiclesOrEquipmentPage.clickLeasingVehiclesRadio(radioOption)
  }

  Then("""^I will see an error 'Tell us if the company provides leasing vehicles or equipments' on the leasing vehicles or equipment page$"""){ () =>
    LeasingVehiclesOrEquipmentPage.checkLeasingVehiclesRadioError()
  }

  Then("""^I will be presented with the vehicle or equipment leasing compliance page$"""){ () =>
    LeasingVehiclesOrEquipmentPage.checkBodyTitle()
  }

  Then("""^the selection of '(.*)' I made on the leasing vehicles or equipments compliance page will be pre-selected$"""){ (option: String) =>
    LeasingVehiclesOrEquipmentPage.checkLeasingVehiclesOption(option)
  }
}
