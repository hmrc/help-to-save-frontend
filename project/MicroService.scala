import sbt.Keys.{testOptions, _}
import sbt.Tests.{Group, SubProcess}
import sbt._
import play.routes.compiler.StaticRoutesGenerator
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._

trait MicroService {

  import uk.gov.hmrc._
  import DefaultBuildSettings._
  import uk.gov.hmrc.{SbtBuildInfo, ShellPrompt, SbtAutoBuildPlugin}
  import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
  import uk.gov.hmrc.versioning.SbtGitVersioning
  import play.sbt.routes.RoutesKeys.routesGenerator
  import TestPhases._

  val appName: String

  lazy val appDependencies: Seq[ModuleID] = ???
  lazy val plugins: Seq[Plugins] = Seq.empty
  lazy val playSettings: Seq[Setting[_]] = Seq.empty

  lazy val scoverageSettings = {
    import scoverage.ScoverageKeys
    Seq(
      // Semicolon-separated list of regexs matching classes to exclude
      ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;.*(config|views.*);.*(AuthService|BuildInfo|Routes).*",
      ScoverageKeys.coverageMinimum := 92,
      ScoverageKeys.coverageFailOnMinimum := true,
      ScoverageKeys.coverageHighlighting := true,
      parallelExecution in Test := false
    )
  }

  def seleniumTestFilter(name: String): Boolean = name.contains("suites")

  def unitTestFilter(name: String): Boolean = !seleniumTestFilter(name)

  lazy val SeleniumTest = config("selenium") extend (Test)


  lazy val microservice = Project(appName, file("."))
    .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin) ++ plugins: _*)
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
    .configs(SeleniumTest)
    .settings(
      inConfig(SeleniumTest)(Defaults.testTasks),
      Keys.fork in SeleniumTest := false,
      unmanagedSourceDirectories in Test += baseDirectory.value / "selenium-system-test/src/test/scala",
      unmanagedResourceDirectories in Test += baseDirectory.value / "selenium-system-test/src/test/resources",
      testOptions in Test := Seq(Tests.Filter(unitTestFilter)),
      testOptions in SeleniumTest := Seq(Tests.Filter(seleniumTestFilter)),
      testOptions in SeleniumTest += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports/html-report"),
      testOptions in SeleniumTest += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports"),
      testOptions in SeleniumTest += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
    )
}

private object TestPhases {

  def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
    tests map {
      test => new Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
    }
}
