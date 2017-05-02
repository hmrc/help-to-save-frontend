package uk.gov.hmrc.integration.cucumber.utils

import uk.gov.hmrc.integration.cucumber.pages.generic.BasePage

import scala.util.Random

object RandomUtils extends BasePage{

  def randString(howManyChars: Integer): String = {
    Random.alphanumeric take howManyChars mkString ""
  }

  def randNumbers(howManyNos: Integer): String = {
    Seq.fill(howManyNos)(Random.nextInt(9)).mkString("")
  }

}
