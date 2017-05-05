package hts.steps

import cucumber.api.scala.{EN, ScalaDsl}
import org.scalatest.Matchers
import hts.driver.StartUpTearDown

trait Steps extends ScalaDsl with EN with Matchers with StartUpTearDown {

}
