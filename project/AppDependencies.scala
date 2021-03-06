import play.core.PlayVersion
import sbt._

object AppDependencies {

  val hmrc = "uk.gov.hmrc"

  val compile = Seq(
    hmrc                %% "govuk-template"             % "5.66.0-play-26",
    hmrc                %% "mongo-caching"              % "7.0.0-play-26",
    hmrc                %% "play-whitelist-filter"      % "3.4.0-play-26",
    hmrc                %% "bootstrap-frontend-play-26" % "5.3.0",
    hmrc                %% "play-ui"                    % "9.2.0-play-26",
    hmrc                %% "play-health"                % "3.16.0-play-26",
    hmrc                %% "play-language"              % "4.12.0-play-26",
    "com.github.kxbmap" %% "configs"                    % "0.4.4",
    "org.typelevel"     %% "cats-core"                  % "2.2.0",
    hmrc                %% "domain"                     % "5.11.0-play-26",
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.1" cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % "1.7.1" % Provided cross CrossVersion.full

  )

  val test = Seq(
    hmrc                     %% "service-integration-test"    % "1.1.0-play-26"    % "test",
    hmrc                     %% "stub-data-generator"         % "0.5.3"             % "test",
    hmrc                     %% "reactivemongo-test"          % "4.22.0-play-26"    % "test",
    "org.scalatest"          %% "scalatest"                   % "3.2.0"             % "test",
    "com.vladsch.flexmark"   % "flexmark-all"                 % "0.35.10"           % "test",
    "org.scalatestplus"      %% "scalatestplus-scalacheck"    % "3.1.0.0-RC2"       % "test",
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.6.0"             % "test",
    "org.scalatestplus.play" %% "scalatestplus-play"          % "3.1.3"             % "test",
    "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % "test"
  )

  // Play 2.6.23 requires akka 2.5.23
  val akka = "com.typesafe.akka"
  val akkaVersion = "2.5.23"
  val overrides = Seq(
    akka %% "akka-stream"    % akkaVersion,
    akka %% "akka-protobuf"  % akkaVersion,
    akka %% "akka-slf4j"     % akkaVersion,
    akka %% "akka-actor"     % akkaVersion,
    akka %% "akka-http-core" % "10.1.12"
  )

}
