import sbt.Keys.{testOptions, _}
import sbt.Tests.{Group, SubProcess}
import sbt._
import play.routes.compiler.StaticRoutesGenerator
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc._
import DefaultBuildSettings._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning
import play.sbt.routes.RoutesKeys.routesGenerator
import TestPhases._

trait MicroService {

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

  lazy val scalariformSettings = {
    import com.typesafe.sbt.SbtScalariform.ScalariformKeys
    import scalariform.formatter.preferences._
    // description of options found here -> https://github.com/scala-ide/scalariform
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(AlignArguments, true)
      .setPreference(AlignParameters, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(CompactControlReadability, false)
      .setPreference(CompactStringConcatenation, false)
      .setPreference(DanglingCloseParenthesis, Preserve)
      .setPreference(DoubleIndentConstructorArguments, true)
      .setPreference(DoubleIndentMethodDeclaration, true)
      .setPreference(FirstArgumentOnNewline, Preserve)
      .setPreference(FirstParameterOnNewline, Preserve)
      .setPreference(FormatXml, true)
      .setPreference(IndentLocalDefs, true)
      .setPreference(IndentPackageBlocks, true)
      .setPreference(IndentSpaces, 2)
      .setPreference(IndentWithTabs, false)
      .setPreference(MultilineScaladocCommentsStartOnFirstLine, false)
      .setPreference(NewlineAtEndOfFile, true)
      .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, false)
      .setPreference(PreserveSpaceBeforeArguments, true)
      .setPreference(RewriteArrowSymbols, true)
      .setPreference(SpaceBeforeColon, false)
      .setPreference(SpaceBeforeContextColon, false)
      .setPreference(SpaceInsideBrackets, false)
      .setPreference(SpaceInsideParentheses, false)
      .setPreference(SpacesAroundMultiImports, false)
      .setPreference(SpacesWithinPatternBinders, true)
  }

  lazy val microservice = Project(appName, file("."))
    .settings(addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17"))
    .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin) ++ plugins: _*)
    .settings(playSettings ++ scoverageSettings: _*)
    .settings(scalaSettings: _*)
    .settings(publishingSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(scalariformSettings: _*)
    .settings(
      libraryDependencies ++= appDependencies,
      retrieveManaged := true,
      evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
      //testGrouping in Test := oneForkedJvmPerTest((definedTests in Test).value),
      routesGenerator := StaticRoutesGenerator
    )
    .configs(IntegrationTest)
    .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
    .settings(
      Keys.fork in IntegrationTest := false,
      unmanagedSourceDirectories in IntegrationTest <<= (baseDirectory in IntegrationTest) (base ⇒ Seq(base / "it")),
      addTestReportOption(IntegrationTest, "int-test-reports"),
      testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
      parallelExecution in IntegrationTest := false)
    .settings(resolvers ++= Seq(
      Resolver.bintrayRepo("hmrc", "releases"),
      Resolver.jcenterRepo,
      "emueller-bintray" at "http://dl.bintray.com/emueller/maven" // for play json schema validator
    ))
    .configs(SeleniumTest)
    .settings(
      inConfig(SeleniumTest)(Defaults.testTasks),
      Keys.fork in SeleniumTest := true,
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
      test ⇒ new Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
    }
}
