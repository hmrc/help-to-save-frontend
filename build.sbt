import scala.language.postfixOps

val appName = "help-to-save-frontend"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(PlayKeys.playDefaultPort := 7000)
  .settings(scalaVersion := "2.13.8")
  .settings(
    majorVersion := 2,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test
  )
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
  .settings(scalacOptions += "-Wconf:cat=unused-imports&src=html/.*:s")
  .settings(CodeCoverageSettings.settings *)
