import sbt.{Compile, taskKey, *}
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.*
import scala.language.postfixOps

val appName = "help-to-save-frontend"
val silencerVersion = "1.7.13"

lazy val appDependencies: Seq[ModuleID] = Seq(ws) ++ AppDependencies.compile ++ AppDependencies.test

lazy val formatMessageQuotes = taskKey[Unit]("Makes sure smart quotes are used in all messages")
lazy val plugins: Seq[Plugins] = Seq.empty
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-Xcheckinit",
    "-feature"
  )
) ++
  scalaSettings ++ defaultSettings() ++ playSettings

lazy val microservice = Project(appName, file("."))
  .settings(commonSettings: _*)
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
  .settings(CodeCoverageSettings.settings *)
