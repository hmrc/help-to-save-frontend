import sbt.{Compile, taskKey, *}
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.*
import wartremover.{WartRemover, Warts}
import scala.language.postfixOps

val appName = "help-to-save-frontend"
val silencerVersion = "1.7.13"

lazy val appDependencies: Seq[ModuleID] = Seq(ws) ++ AppDependencies.compile ++ AppDependencies.test

lazy val formatMessageQuotes = taskKey[Unit]("Makes sure smart quotes are used in all messages")
lazy val plugins: Seq[Plugins] = Seq.empty
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*Reverse.*;.*(config|views.*);.*(AuthService|BuildInfo|Routes).*",
    ScoverageKeys.coverageMinimumStmtTotal := 90,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test / parallelExecution := false
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
    Wart.Var,
    Wart.ListAppend,
    Wart.AsInstanceOf,
    Wart.PlatformDefault,
    Wart.NonUnitStatements,
    Wart.Equals,
    Wart.JavaSerializable,
    Wart.StringPlusAny,
    Wart.SeqApply,
    Wart.Any,
    Wart.Product,
    Wart.Serializable,
    Wart.GlobalExecutionContext
  )

  Seq(
     (Compile / compile / WartRemover.autoImport.wartremoverErrors) ++= Warts.allBut(excludedWarts: _*),
    // disable some wart remover checks in tests - (Any, Null, PublicInference) seems to struggle with
    // scalamock, (Equals) seems to struggle with stub generator AutoGen and (NonUnitStatements) is
    // imcompatible with a lot of WordSpec
    (Test / compile / WartRemover.autoImport.wartremoverErrors) --= Seq(
      Wart.Any,
      Wart.Equals,
      Wart.Null,
      Wart.NonUnitStatements,
      Wart.PublicInference
    ),
    (Compile / compile / wartremoverExcluded) ++=
      (Compile / routes).value)
}

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-Xcheckinit",
    "-feature",
  )
) ++
  scalaSettings ++ defaultSettings() ++ scoverageSettings ++ playSettings

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
    Seq(play.sbt.PlayScala, SbtDistributablesPlugin) ++ plugins: _*
  )
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(PlayKeys.playDefaultPort := 7000)
  .settings(scalaVersion := "2.13.8")
  .settings(
    majorVersion := 2,
    libraryDependencies ++= appDependencies
  )
  .settings(scalacOptions ++= List(
    "-P:silencer:pathFilters=routes;views"
  ),
    libraryDependencies ++= Seq(
      compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
      "com.github.ghik" % "silencer-lib" % silencerVersion % Provided cross CrossVersion.full
    )
  )
  .settings(
    formatMessageQuotes := {
      import sys.process._
      if (!System.getProperty("os.name").startsWith("Windows")) {
        val rightQuoteReplace =
          List("sed", "-i", s"""s/&rsquo;\\|''/’/g""", s"${baseDirectory.value.getAbsolutePath}/conf/messages") !
        val leftQuoteReplace =
          List("sed", "-i", s"""s/&lsquo;/‘/g""", s"${baseDirectory.value.getAbsolutePath}/conf/messages") !

        if (rightQuoteReplace != 0 || leftQuoteReplace != 0) {
          logger.log(Level.Warn, "WARNING: could not replace quotes with smart quotes")
        }
      }
    },
    compile := ((Compile / compile) dependsOn formatMessageQuotes).value
  )
