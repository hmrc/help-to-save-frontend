import play.core.PlayVersion
import sbt._

object AppDependencies {

  val hmrc = "uk.gov.hmrc"

  val compile = Seq(
    hmrc                %% "govuk-template"             % "5.72.0-play-28",
    hmrc                %% "mongo-caching"              % "7.0.0-play-28",
    hmrc                %% "bootstrap-frontend-play-28" % "5.3.0",
    hmrc                %% "play-ui"                    % "9.5.0-play-28",
    hmrc                %% "play-language"              % "5.1.0-play-28",
    "com.github.kxbmap" %% "configs"                     % "0.4.4",
    "org.typelevel"     %% "cats-core"                  % "2.6.1",
    hmrc                %% "domain"                     % "6.2.0-play-28",
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.1" cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % "1.7.1" % Provided cross CrossVersion.full

  )

  val test = Seq(
    hmrc                     %% "service-integration-test"    % "1.1.0-play-28"     % "test",
    hmrc                     %% "stub-data-generator"         % "0.5.3"             % "test",
    hmrc                     %% "reactivemongo-test"          % "5.0.0-play-28"     % "test",
    "org.scalatestplus"      %% "scalatestplus-scalacheck"    % "3.1.0.0-RC2"       % "test",
    "org.scalatestplus"      %% "scalatestplus-mockito"       % "1.0.0-M2"          % "test",
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.6.0"             % "test",
    "com.typesafe.play"      %% "play"                        % "2.8.8"             % "test",
    "com.miguno.akka"        %% "akka-mock-scheduler"         % "0.5.5"             % "test",
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"      % "5.16.0"            % "test",
    "com.vladsch.flexmark"    %  "flexmark-all"                 % "0.35.10"           % "test"
  )

  def apply(): Seq[ModuleID] = compile ++ test

  val akka = "com.typesafe.akka"
  val akkaVersion = "2.6.14"
  val akkaHttpVersion = "10.2.6"
  val overrides = Seq(
    akka %% "akka-stream"    % akkaVersion,
    akka %% "akka-protobuf"  % akkaVersion,
    akka %% "akka-slf4j"     % akkaVersion,
    akka %% "akka-actor"     % akkaVersion,
    akka %% "akka-http-core" % "10.1.12"
  )

}
