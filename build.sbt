import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import play.core.PlayVersion
import sbt.Keys.{testOptions, _}
import sbt.{Compile, _}
import scalariform.formatter.preferences._
import uk.gov.hmrc.DefaultBuildSettings.{addTestReportOption, defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.{SbtAutoBuildPlugin, _}
import wartremover.{Wart, Warts, wartremoverErrors, wartremoverExcluded}
import uk.gov.hmrc.ServiceManagerPlugin.Keys.itDependenciesList
import uk.gov.hmrc.ExternalService


val appName = "help-to-save-frontend"

lazy val appDependencies: Seq[ModuleID] = dependencies ++ testDependencies() ++ testDependencies("it")
lazy val externalServices = List(ExternalService("AUTH"), ExternalService("INDIVIDUALS_MATCHING_API"), ExternalService("DES"))

val dependencies = Seq(
  ws,
  "uk.gov.hmrc" %% "govuk-template" % "5.40.0-play-26",
  "uk.gov.hmrc" %% "mongo-caching" % "6.6.0-play-26",
  "org.typelevel" %% "cats-core" % "1.5.0",
  "uk.gov.hmrc" %% "auth-client" % "2.29.0-play-26",
  "com.github.kxbmap" %% "configs" % "0.4.4",
  "uk.gov.hmrc" %% "play-whitelist-filter" % "2.0.0",
  "uk.gov.hmrc" %% "bootstrap-play-26" % "1.0.0",
  "uk.gov.hmrc" %% "play-ui" % "8.1.0-play-26",
  "uk.gov.hmrc" %% "play-language" % "3.4.0"
)

def testDependencies(scope: String = "test") = Seq(
  "uk.gov.hmrc" %% "service-integration-test" % "0.9.0-play-26" % scope,
  "uk.gov.hmrc" %% "domain" % "5.3.0" % scope,
  "org.scalatest" %% "scalatest" % "3.0.8" % scope,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % scope,
  "uk.gov.hmrc" %% "stub-data-generator" % "0.5.3" % scope,
  "uk.gov.hmrc" %% "reactivemongo-test" % "4.15.0-play-26" % scope,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.0" % scope
)

lazy val formatMessageQuotes = taskKey[Unit]("Makes sure smart quotes are used in all messages")

lazy val plugins: Seq[Plugins] = Seq.empty
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;Reverse.*;.*(config|views.*);.*(AuthService|BuildInfo|Routes).*",
    ScoverageKeys.coverageMinimum := 91,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

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

def wartRemoverSettings(ignoreFiles: File ⇒ Seq[File] = _ ⇒ Seq.empty[File]) = {
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

  Seq(
    wartremoverErrors in (Compile, compile) ++= Warts.allBut(excludedWarts: _*),
    // disable some wart remover checks in tests - (Any, Null, PublicInference) seems to struggle with
    // scalamock, (Equals) seems to struggle with stub generator AutoGen and (NonUnitStatements) is
    // imcompatible with a lot of WordSpec
    wartremoverErrors in (Test, compile) --= Seq(Wart.Any, Wart.Equals, Wart.Null, Wart.NonUnitStatements, Wart.PublicInference),
    wartremoverExcluded in (Compile, compile) ++=
      routes.in(Compile).value ++
        ignoreFiles(baseDirectory.value) ++
        (baseDirectory.value ** "*.sc").get ++
        Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala") ++
        (baseDirectory.value / "app" / "uk" / "gov" / "hmrc" / "helptosavefrontend" / "config").get
  )
}

lazy val commonSettings = Seq(
  addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17"),
  majorVersion := 2,
  evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
  resolvers ++= Seq(
    Resolver.bintrayRepo("hmrc", "releases"),
    Resolver.jcenterRepo,
    "emueller-bintray" at "http://dl.bintray.com/emueller/maven" // for play json schema validator
  ),
  scalacOptions ++= Seq("-Xcheckinit", "-feature")
) ++ scalaSettings ++ publishingSettings ++ defaultSettings() ++ scalariformSettings ++ scoverageSettings ++ playSettings


lazy val microservice = Project(appName, file("."))
  .settings(commonSettings: _*)
  .settings(wartRemoverSettings( baseDirectory ⇒
      (baseDirectory ** "HealthCheck.scala").get ++
      (baseDirectory ** "HealthCheckRunner.scala").get ++
      (baseDirectory ** "Lock.scala").get
  ))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins: _*)
  .settings(PlayKeys.playDefaultPort := 7000)
  .settings(
      libraryDependencies ++= appDependencies
    //retrieveManaged := true,
    //testGrouping in Test := oneForkedJvmPerTest((definedTests in Test).value),

  )
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
  .settings(itDependenciesList := externalServices)
  .settings(
    Keys.fork in IntegrationTest := false,
    unmanagedSourceDirectories in IntegrationTest := Seq((baseDirectory in IntegrationTest).value / "it"),
    addTestReportOption(IntegrationTest, "int-test-reports"),
    //testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
    parallelExecution in IntegrationTest := false
  )
  .settings(
    formatMessageQuotes := {
      import sys.process._
      val rightQuoteReplace = (List("sed", "-i", s"""s/&rsquo;\\|''/’/g""", s"${baseDirectory.value.getAbsolutePath}/conf/messages") !)
      val leftQuoteReplace = (List("sed", "-i", s"""s/&lsquo;/‘/g""", s"${baseDirectory.value.getAbsolutePath}/conf/messages") !)

      if(rightQuoteReplace != 0 || leftQuoteReplace != 0){ logger.log(Level.Warn, "WARNING: could not replace quotes with smart quotes") }
    },
    compile := ((compile in Compile) dependsOn formatMessageQuotes).value
  )

lazy val selenium = (project in file("selenium-system-test"))
  .dependsOn(microservice)
  .settings(commonSettings: _*)
  .settings(wartRemoverSettings(): _*)
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins: _*)
  .settings(
    libraryDependencies ++= testDependencies() ++ Seq(
      "io.cucumber"           %% "cucumber-scala"         % "4.7.1",
      "io.cucumber"           %  "cucumber-junit"         % "4.7.1",
      "uk.gov.hmrc"           %% "webdriver-factory"      % "0.7.0"   exclude( "org.slf4j","slf4j-simple")
    )
  )
  .settings(
    Keys.fork in Test := true,
    scalaSource in Test := baseDirectory.value / "src" / "test",
    resourceDirectory in Test := baseDirectory.value / "src" / "test" / "resources",
    testOptions in Test := Seq(Tests.Filter(name ⇒  name.contains("suites"))),
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports/html-report"),
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports"),
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
  )

lazy val zap = (project in file("zap"))
  .settings(commonSettings: _*)
  .settings(wartRemoverSettings(): _*)
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins: _*)
  .settings(libraryDependencies += "uk.gov.hmrc" %% "zap-automation" % "2.1.0")
  .settings(
    scalaSource in Test := baseDirectory.value / "src" / "test",
    resourceDirectory in Test := baseDirectory.value / "src" / "test" /  "resources"
  )
