import sbt.{Compile, taskKey, _}
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, scalaSettings}
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning
import wartremover.{WartRemover, Warts}

import scala.language.postfixOps

val appName = "help-to-save-frontend"

lazy val appDependencies: Seq[ModuleID] = Seq(ws) ++ AppDependencies.compile ++ AppDependencies.test

dependencyOverrides ++= AppDependencies.overrides

lazy val formatMessageQuotes = taskKey[Unit]("Makes sure smart quotes are used in all messages")
lazy val plugins: Seq[Plugins] = Seq.empty
lazy val playSettings: Seq[Setting[_]] = Seq.empty

val akkaVersion     = "2.5.23"

val akkaHttpVersion = "10.0.15"

dependencyOverrides += "com.typesafe.akka" %% "akka-stream"    % akkaVersion

dependencyOverrides += "com.typesafe.akka" %% "akka-protobuf"  % akkaVersion

dependencyOverrides += "com.typesafe.akka" %% "akka-slf4j"     % akkaVersion

dependencyOverrides += "com.typesafe.akka" %% "akka-actor"     % akkaVersion

dependencyOverrides += "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*Reverse.*;.*(config|views.*);.*(AuthService|BuildInfo|Routes).*",
    ScoverageKeys.coverageMinimum := 92,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
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
    Wart.Var
  )

  Seq(
    WartRemover.autoImport.wartremoverErrors in (Compile, compile) ++= Warts.allBut(excludedWarts: _*),
    // disable some wart remover checks in tests - (Any, Null, PublicInference) seems to struggle with
    // scalamock, (Equals) seems to struggle with stub generator AutoGen and (NonUnitStatements) is
    // imcompatible with a lot of WordSpec
    WartRemover.autoImport.wartremoverErrors in (Test, compile) --= Seq(
      Wart.Any,
      Wart.Equals,
      Wart.Null,
      Wart.NonUnitStatements,
      Wart.PublicInference
    ),
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
  resolvers += Resolver.bintrayRepo("hmrc", "releases"),
  scalacOptions ++= Seq("-Xcheckinit", "-feature")
) ++
  scalaSettings ++ publishingSettings ++ defaultSettings() ++ scoverageSettings ++ playSettings

lazy val microservice = Project(appName, file("."))
  .settings(commonSettings: _*)
  .settings(
    wartRemoverSettings(
      baseDirectory ⇒
        (baseDirectory ** "HealthCheck.scala").get ++
          (baseDirectory ** "HealthCheckRunner.scala").get ++
          (baseDirectory ** "Lock.scala").get
    )
  )
  .enablePlugins(
    Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins: _*
  )
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(PlayKeys.playDefaultPort := 7000)
  .settings(
    libraryDependencies ++= appDependencies
  )
  .settings(scalaVersion := "2.12.11")
  .settings(scalacOptions ++= List(
    // Warn if an import selector is not referenced.
//    "-P:silencer:globalFilters=Unused import",
    "-P:silencer:pathFilters=html",
    "-P:silencer:pathFilters=routes"
  ))
  .settings(
    formatMessageQuotes := {
      import sys.process._
      val rightQuoteReplace =
        List("sed", "-i", s"""s/&rsquo;\\|''/’/g""", s"${baseDirectory.value.getAbsolutePath}/conf/messages") !
      val leftQuoteReplace =
        List("sed", "-i", s"""s/&lsquo;/‘/g""", s"${baseDirectory.value.getAbsolutePath}/conf/messages") !

      if (rightQuoteReplace != 0 || leftQuoteReplace != 0) {
        logger.log(Level.Warn, "WARNING: could not replace quotes with smart quotes")
      }
    },
    compile := ((compile in Compile) dependsOn formatMessageQuotes).value
  )
