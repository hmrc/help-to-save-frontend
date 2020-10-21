import play.core.PlayVersion
import sbt._

object AppDependencies {

  val hmrc = "uk.gov.hmrc"

  val compile = Seq(
    hmrc                %% "govuk-template"             % "5.57.0-play-26",
    hmrc                %% "mongo-caching"              % "6.15.0-play-26",
    hmrc                %% "auth-client"                % "3.2.0-play-26",
    hmrc                %% "play-whitelist-filter"      % "3.4.0-play-26",
    hmrc                %% "bootstrap-frontend-play-26" % "2.25.0",
    hmrc                %% "play-ui"                    % "8.12.0-play-26",
    hmrc                %% "play-health"                % "3.15.0-play-26",
    hmrc                %% "play-language"              % "4.4.0-play-26",
    "com.github.kxbmap" %% "configs"                    % "0.4.4",
    "org.typelevel"     %% "cats-core"                  % "2.2.0",
    hmrc                %% "domain"                     % "5.10.0-play-26"
  )

  val test = Seq(
    hmrc                     %% "service-integration-test"    % "0.12.0-play-26"    % "test",
    hmrc                     %% "stub-data-generator"         % "0.5.3"             % "test",
    hmrc                     %% "reactivemongo-test"          % "4.21.0-play-26"    % "test",
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
