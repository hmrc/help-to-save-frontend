package hts.pages

import hts.utils.Configuration
import org.openqa.selenium.WebDriver

object GiveEmailPage extends Page {

  val url: String = s"${Configuration.host}/help-to-save/register/give-email"

  def continue()(implicit driver: WebDriver): Unit = click on "continue"
}
