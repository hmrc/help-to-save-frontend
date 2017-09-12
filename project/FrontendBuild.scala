import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion

object FrontendBuild extends Build with MicroService {

  val appName = "help-to-save-frontend"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "frontend-bootstrap" % "8.6.0",
    "uk.gov.hmrc" %% "play-partials" % "6.0.0",
    "uk.gov.hmrc" %% "play-config" % "4.3.0",
    "uk.gov.hmrc" %% "logback-json-logger" % "3.1.0",
    "uk.gov.hmrc" %% "govuk-template" % "5.8.0",
    "uk.gov.hmrc" %% "play-health" % "2.1.0",
    "uk.gov.hmrc" %% "http-caching-client" % "7.0.0",
    "uk.gov.hmrc" %% "play-ui" % "7.5.0",
    "org.typelevel" %% "cats" % "0.9.0",
    "uk.gov.hmrc" %% "play-auth" % "2.2.0",
    "uk.gov.hmrc" %% "domain" % "4.1.0",
    "com.github.kxbmap" %% "configs" % "0.4.4",
    "com.eclipsesource" %% "play-json-schema-validator" % "0.8.9",
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.0.0"
  )

  def test(scope: String = "test") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "2.3.0" % scope,
    "org.scalatest" %% "scalatest" % "3.0.1" % scope,
    "org.jsoup" % "jsoup" % "1.8.1" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % scope,
    "uk.gov.hmrc" %% "stub-data-generator" % "0.4.0" % scope,
    // below for selenium tests
    "info.cukes" % "cucumber-junit" % "1.2.4" % scope,
    "info.cukes" % "cucumber-picocontainer" % "1.2.4" % scope,
    "info.cukes" %% "cucumber-scala" % "1.2.4" % scope,
    "org.seleniumhq.selenium" % "selenium-java" % "2.53.1" % scope,
    "org.seleniumhq.selenium" % "selenium-firefox-driver" % "2.53.1" % scope
  )

}
