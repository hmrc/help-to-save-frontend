import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import play.core.PlayVersion
import sbt.Keys.{testOptions, _}
import sbt.{Compile, _}
import scalariform.formatter.preferences._
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.{SbtAutoBuildPlugin, _}
import wartremover.{Wart, Warts, wartremoverErrors, wartremoverExcluded}

val appName = "help-to-save-frontend"

lazy val appDependencies: Seq[ModuleID] = dependencies ++ testDependencies() ++ testDependencies("it")

val dependencies = Seq(
  ws,
  "uk.gov.hmrc" %% "govuk-template" % "5.22.0",
  "uk.gov.hmrc" %% "http-caching-client" % "7.1.0",
  "org.typelevel" %% "cats-core" % "1.1.0",
  "uk.gov.hmrc" %% "auth-client" % "2.17.0-play-25",
  "com.github.kxbmap" %% "configs" % "0.4.4",
  "uk.gov.hmrc" %% "play-whitelist-filter" % "2.0.0",
  "uk.gov.hmrc" %% "bootstrap-play-25" % "3.5.0",
  "uk.gov.hmrc" %% "play-ui" % "7.17.0"
)

def testDependencies(scope: String = "test") = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "3.0.0" % scope,
  "uk.gov.hmrc" %% "domain" % "5.1.0" % scope,
  "org.scalatest" %% "scalatest" % "3.0.5" % scope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % scope,
  "uk.gov.hmrc" %% "stub-data-generator" % "0.5.3" % scope,
  // below for selenium tests
  "info.cukes" % "cucumber-junit" % "1.2.5" % scope,
  "info.cukes" % "cucumber-picocontainer" % "1.2.5" % scope,
  "info.cukes" %% "cucumber-scala" % "1.2.5" % scope,
  "org.seleniumhq.selenium" % "selenium-java" % "3.13.0" % scope,
  "org.seleniumhq.selenium" % "selenium-firefox-driver" % "3.13.0" % scope,
  "org.seleniumhq.selenium" % "selenium-htmlunit-driver" % "2.52.0" % scope,
  "uk.gov.hmrc" %% "zap-automation" % "0.17.0" % scope,
  "com.google.guava" % "guava" % "25.1-jre" % scope
)

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

def seleniumTestFilter(name: String): Boolean = name.contains("suites") && !name.contains("Zap")

def zapTestFilter(name: String): Boolean = name.contains("Zap") && !name.contains("suites")

def unitTestFilter(name: String): Boolean = !seleniumTestFilter(name) && !zapTestFilter(name)

lazy val SeleniumTest = config("selenium") extend Test

lazy val ZapTest = config("zap") extend Test

lazy val scalariformSettings = {
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

lazy val wartRemoverSettings = {
  // list of warts here: http://www.wartremover.org/doc/warts.html
  val excludedWarts = Seq(
    Wart.DefaultArguments,
    Wart.FinalCaseClass,
    Wart.FinalVal,
    Wart.ImplicitConversion,
    Wart.ImplicitParameter,
    Wart.LeakingSealed,
    Wart.Nothing,
    Wart.Overloading,
    Wart.ToString,
    Wart.Var)

  wartremoverErrors in(Compile, compile) ++= Warts.allBut(excludedWarts: _*)
}

lazy val microservice = Project(appName, file("."))
  .settings(addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17"))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins: _*)
  .settings(playSettings ++ scoverageSettings: _*)
  .settings(majorVersion := 2)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(PlayKeys.playDefaultPort := 7000)
  .settings(scalariformSettings: _*)
  .settings(wartRemoverSettings)
  // disable some wart remover checks in tests - (Any, Null, PublicInference) seems to struggle with
  // scalamock, (Equals) seems to struggle with stub generator AutoGen and (NonUnitStatements) is
  // imcompatible with a lot of WordSpec
  .settings(wartremoverErrors in(Test, compile) --= Seq(Wart.Any, Wart.Equals, Wart.Null, Wart.NonUnitStatements, Wart.PublicInference))
  .settings(wartremoverExcluded ++=
    routes.in(Compile).value ++
      (baseDirectory.value ** "*.sc").get ++
      (baseDirectory.value ** "HealthCheck.scala").get ++
      (baseDirectory.value ** "HealthCheckRunner.scala").get ++
      (baseDirectory.value ** "Lock.scala").get ++
      Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala")
  )
  .settings(
      libraryDependencies ++= appDependencies,
    //retrieveManaged := true,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    //testGrouping in Test := oneForkedJvmPerTest((definedTests in Test).value),
    routesGenerator := StaticRoutesGenerator
  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest := Seq((baseDirectory in IntegrationTest).value / "it"),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    //testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false)
  .settings(resolvers ++= Seq(
    Resolver.bintrayRepo("hmrc", "releases"),
    Resolver.jcenterRepo,
    "emueller-bintray" at "http://dl.bintray.com/emueller/maven" // for play json schema validator
  ))
  .configs(SeleniumTest)
  .configs(ZapTest)
  .settings(
    inConfig(SeleniumTest)(Defaults.testTasks),
    inConfig(ZapTest)(Defaults.testTasks),
    Keys.fork in SeleniumTest := true,
    unmanagedSourceDirectories in Test += baseDirectory.value / "selenium-system-test/src/test/scala",
    unmanagedResourceDirectories in Test += baseDirectory.value / "selenium-system-test/src/test/resources",
    unmanagedSourceDirectories in Test += baseDirectory.value / "zap/src/test/scala",
    testOptions in Test := Seq(Tests.Filter(unitTestFilter)),
    testOptions in SeleniumTest := Seq(Tests.Filter(seleniumTestFilter)),
    testOptions in ZapTest := Seq(Tests.Filter(zapTestFilter)),
    testOptions in SeleniumTest += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports/html-report"),
    testOptions in SeleniumTest += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports"),
    testOptions in SeleniumTest += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
  )
