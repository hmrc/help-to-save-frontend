import play.sbt.routes.RoutesKeys

val appName = "help-to-save-frontend"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(onLoadMessage := "")
  .settings(PlayKeys.playDefaultPort := 7003)
  .settings(scalaVersion := "3.7.1")
  .settings(
    RoutesKeys.routesImport += "uk.gov.hmrc.play.bootstrap.binders.RedirectUrl",
    majorVersion := 2,
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test()
  )
  .settings(scalacOptions += "-Wconf:src=routes/.*:s,src=txt/.*:s")
  .settings(scalacOptions ++=Seq("-source:3.0-migration", "-rewrite"))
  // silence authprovider warnings - we need to use the deprecated authprovider
  .settings(
    scalacOptions ++= Seq(
      "-feature",
      "-Wconf:cat=deprecation:w,cat=feature:w,src=target/.*:s,msg=Flag.*repeatedly:s"
    )
  )
  .settings(CodeCoverageSettings.settings *)
  .settings(scalafmtOnCompile := true)
  // Disable default sbt Test options (might change with new versions of bootstrap)
  .settings(
    Test / testOptions -= Tests.Argument("-o", "-u", "target/test-reports", "-h", "target/test-reports/html-report")
  )
  // Suppress successful events in Scalatest in standard output (-o)
  // Options described here: https://www.scalatest.org/user_guide/using_scalatest_with_sbt
  .settings(
    Test / testOptions += Tests.Argument(
      TestFrameworks.ScalaTest,
      "-oNCHPQR",
      "-u",
      "target/test-reports",
      "-h",
      "target/test-reports/html-report"
    )
  )
