package rnrb.suites

import cucumber.api.CucumberOptions
import cucumber.api.junit.Cucumber
import org.junit.runner.RunWith

@RunWith(classOf[Cucumber])
@CucumberOptions(
  features = Array("src/test/resources/features"),
  glue = Array("rnrb.steps"),
  format = Array ("pretty", "html:target/cucumber", "json:target/cucumber.json"),
  tags = Array("@Suite")
)
class Runner {
}
