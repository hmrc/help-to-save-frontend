package rnrb.steps

import cucumber.api.scala.{EN, ScalaDsl}
import org.scalatest.Matchers
import rnrb.driver.StartUpTearDown

trait Steps extends ScalaDsl with EN with Matchers with StartUpTearDown {

}
