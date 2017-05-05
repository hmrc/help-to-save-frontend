name := "help-to-save-e2e-Selenium-tests"

version := "1.0"

scalaVersion := "2.11.8"

val hmrcRepoHost = java.lang.System.getProperty("hmrc.repo.host", "https://nexus-preview.tax.service.gov.uk")

resolvers ++= Seq(
  "hmrc-snapshots" at hmrcRepoHost + "/content/repositories/hmrc-snapshots",
  "hmrc-releases" at hmrcRepoHost + "/content/repositories/hmrc-releases",
  "typesafe-releases" at hmrcRepoHost + "/content/repositories/typesafe-releases")

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "HMRC Bintray" at "https://dl.bintray.com/hmrc/releases"

resolvers += "HMRC Bintray RCs" at "https://dl.bintray.com/hmrc/release-candidates"

libraryDependencies ++= Seq(
  "com.codeborne" % "phantomjsdriver" % "1.2.1",
  "org.scala-lang" % "scala-library" % "2.11.8",
  "info.cukes" % "cucumber-junit" % "1.2.4",
  "info.cukes" % "cucumber-picocontainer" % "1.2.4",
  "junit" % "junit" % "4.12" % "test",
  "com.novocode" % "junit-interface" % "0.11" % "test",
  "uk.gov.hmrc" %% "scala-webdriver" % "5.4.0",
  "org.apache.poi" % "poi-ooxml" % "3.13",
  "org.apache.poi" % "poi-ooxml-schemas" % "3.13",
  "log4j" % "log4j" % "1.2.17",
  "org.seleniumhq.selenium" % "selenium-java" % "2.53.1",
  "org.seleniumhq.selenium" % "selenium-firefox-driver" % "2.53.1",
  "com.typesafe.play" %% "play-iteratees" % "2.4.6",
  "org.mongodb" %% "casbah" % "3.1.0",
  "com.typesafe.play" %% "play-json" % "2.4.6",
  "info.cukes" % "cucumber-scala_2.11" % "1.2.2",
  "org.pegdown" % "pegdown" % "1.6.0" % "test",
  "org.scalatest" %% "scalatest" % "2.2.4",
  "uk.gov.hmrc" %% "accessibility-driver" % "1.6.0",
  "com.netaporter" %% "scala-uri" % "0.4.14"
)


