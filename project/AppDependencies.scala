import play.core.PlayVersion
import sbt._

object AppDependencies {

  val hmrc = "uk.gov.hmrc"

  val compile = Seq(
    hmrc                %% "govuk-template"             % "5.72.0-play-28",
    hmrc                %% "mongo-caching"              % "6.15.0-play-26",
    hmrc                %% "auth-client"                % "3.0.0-play-26",
    hmrc                %% "play-whitelist-filter"      % "3.4.0-play-26",
    hmrc                %% "bootstrap-frontend-play-26" % "2.24.0",
    hmrc                %% "play-ui"                    % "8.11.0-play-26",
    hmrc                %% "play-health"                % "3.15.0-play-26",
    hmrc                %% "play-language"              % "4.3.0-play-26",
    "com.github.kxbmap" %% "configs"                    % "0.4.4",
    "org.typelevel"     %% "cats-core"                  % "2.1.1",
    hmrc                %% "domain"                     % "5.9.0-play-26",
    compilerPlugin("com.github.ghik" % "silencer-plugin"    % "1.7.1" cross CrossVersion.full),
    "com.github.ghik"             % "silencer-lib"                      % "1.7.1" % Provided cross CrossVersion.full
  )

//  val compile = Seq(
//    hmrc                %% "govuk-template"             % "5.69.0-play-28",
//    hmrc                %% "mongo-caching"              % "7.0.0-play-28",
//    hmrc                %% "auth-client"                % "5.7.0-play-28",
//    hmrc                %% "play-whitelist-filter"      % "3.4.0-play-27",
//    hmrc                %% "bootstrap-frontend-play-26" % "2.24.0",
//    hmrc                %% "play-ui"                    % "9.6.0-play-28",
//    hmrc                %% "play-health"                % "3.16.0-play-27",
//    hmrc                %% "play-language"              % "5.1.0-play-28",
//    "com.github.kxbmap" %% "configs"                    % "0.4.4",
//    "org.typelevel"     %% "cats-core"                  % "2.1.1",
//    hmrc                %% "domain"                     % "6.2.0-play-28"
//  )

  val test = Seq(
    hmrc                     %% "service-integration-test"    % "0.12.0-play-26"    % "test",
    hmrc                     %% "stub-data-generator"         % "0.5.3"             % "test",
    hmrc                     %% "reactivemongo-test"          % "4.21.0-play-26"    % "test",
    "org.scalatest"          %% "scalatest"                   % "3.2.9"             % "test",
    "com.vladsch.flexmark"   %  "flexmark-all"                % "0.35.10"           % "test",
    "org.scalatestplus"      %% "scalatestplus-scalacheck"    % "3.1.0.0-RC2"       % "test",
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.6.0"             % "test",
    "org.scalatestplus.play" %% "scalatestplus-play"          % "5.1.0"             % "test",
    "com.typesafe.play"      %% "play-test"                   % PlayVersion.current % "test"
  )

}
