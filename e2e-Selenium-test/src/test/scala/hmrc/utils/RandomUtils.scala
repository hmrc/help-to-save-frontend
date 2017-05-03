package hmrc.utils

import hmrc.pages.BasePage

import scala.util.Random

object RandomUtils extends BasePage{

  def randString(howManyChars: Integer): String = {
    Random.alphanumeric take howManyChars mkString ""
  }

  def randNumbers(howManyNos: Integer): String = {
    Seq.fill(howManyNos)(Random.nextInt(9)).mkString("")
  }

}
