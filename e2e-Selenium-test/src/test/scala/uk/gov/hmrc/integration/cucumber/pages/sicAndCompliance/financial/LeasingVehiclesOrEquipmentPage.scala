package uk.gov.hmrc.integration.cucumber.pages.sicAndCompliance.financial

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage


object LeasingVehiclesOrEquipmentPage extends BasePage{

  override val url: String = s"$basePageUrl/involved-in-leasing-vehicles-or-equipment"

  def checkBodyTitle(): Boolean = checkBodyTitle(getMessage("fclvep.body.header"))

  def leasingVehiclesRadios: RadioButtonGroup = radioButtonGroup("leaseVehiclesRadio")

  def clickLeasingVehiclesRadio(option: String): Unit = clickOptionYesNo(option, leasingVehiclesRadios, "true", "false")

  def checkLeasingVehiclesOption(option: String): Unit = checkOptionYesNo(option, leasingVehiclesRadios, "true", "false")

  def checkLeasingVehiclesRadioError(): Unit = validateErrorMessages("leaseVehiclesRadio", "error.leaseVehicleRadio.noSelection")

}
