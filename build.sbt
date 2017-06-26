import play.core.PlayVersion
import play.sbt.PlayScala
import sbt.Keys.name
import sbt.Tests.{Group, SubProcess}
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings

name := "help-to-save-frontend"
val appName = "help-to-save-frontend"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

val compile = Seq(
  ws,
  "uk.gov.hmrc" %% "frontend-bootstrap" % "7.22.0",
  "uk.gov.hmrc" %% "play-partials" % "5.3.0",
  "uk.gov.hmrc" %% "play-config" % "4.3.0",
  "uk.gov.hmrc" %% "logback-json-logger" % "3.1.0",
  "uk.gov.hmrc" %% "govuk-template" % "5.2.0",
  "uk.gov.hmrc" %% "play-health" % "2.1.0",
  "uk.gov.hmrc" %% "http-caching-client" % "6.2.0",
  "uk.gov.hmrc" %% "play-ui" % "7.2.1",
  "org.typelevel" %% "cats" % "0.9.0",
  "uk.gov.hmrc" %% "play-auth" % "1.1.0",
  "uk.gov.hmrc" %% "domain" % "4.1.0",
  "com.github.kxbmap" %% "configs" % "0.4.4"
)

def test(scope: String = "test") = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "2.3.0" % scope,
  "org.scalatest" %% "scalatest" % "3.0.1" % scope,
  "org.pegdown" % "pegdown" % "1.6.0" % scope,
  "org.jsoup" % "jsoup" % "1.8.1" % scope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.5.0" % scope,
  // below for selenium tests
  "com.codeborne" % "phantomjsdriver" % "1.4.3" % scope,
  "info.cukes" % "cucumber-junit" % "1.2.4" % scope,
  "info.cukes" % "cucumber-picocontainer" % "1.2.4" % scope,
  "info.cukes" %% "cucumber-scala" % "1.2.2" % scope,
  "org.seleniumhq.selenium" % "selenium-java" % "2.53.1" % scope,
  "org.seleniumhq.selenium" % "selenium-firefox-driver" % "2.53.1" % scope
)

lazy val appDependencies: Seq[ModuleID] = compile ++ test()
lazy val plugins: Seq[Plugins] = Seq.empty
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val scoverageSettings = {
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;.*(config|views.*);.*(AuthService|BuildInfo|Routes).*",
    ScoverageKeys.coverageMinimum := 92,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

def seleniumTestFilter(name: String): Boolean = name.endsWith("SeleniumSystemTest")

def unitTestFilter(name: String): Boolean = !seleniumTestFilter(name)

lazy val SeleniumTest = config("selenium") extend Test

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin) ++ plugins: _*)
  .settings(playSettings ++ scoverageSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(
    libraryDependencies ++= appDependencies,
    retrieveManaged := true,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    routesGenerator := StaticRoutesGenerator
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest <<= (baseDirectory in IntegrationTest) (base => Seq(base / "it")),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false)
  .settings(resolvers ++= Seq(
    Resolver.bintrayRepo("hmrc", "releases"),
    Resolver.jcenterRepo
  ))
  .configs(SeleniumTest)
  .settings(
    inConfig(SeleniumTest)(Defaults.testTasks),
    Keys.fork in SeleniumTest := true,
    unmanagedSourceDirectories in Test += baseDirectory.value / "selenium-system-test",
    testOptions in Test := Seq(Tests.Filter(unitTestFilter)),
    testOptions in SeleniumTest := Seq(Tests.Filter(seleniumTestFilter))
  )

private def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
  tests map {
    test => Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
  }
