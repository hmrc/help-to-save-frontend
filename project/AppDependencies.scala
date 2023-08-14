import sbt._

object AppDependencies {

  val hmrc = "uk.gov.hmrc"
  val playVersion = "play-28"
  val bootstrapBackendVersion = "5.25.0"

  val compile = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"         % "0.68.0",
    hmrc                %% "bootstrap-frontend-play-28" % bootstrapBackendVersion,
    "com.github.kxbmap" %% "configs"                    % "0.6.1",
    "org.typelevel"     %% "cats-core"                  % "2.6.1",
    hmrc                %% "domain"                     % s"8.3.0-$playVersion",
    compilerPlugin("com.github.ghik" % "silencer-plugin" % "1.7.5" cross CrossVersion.full),
    "com.github.ghik" % "silencer-lib" % "1.7.5" % Provided cross CrossVersion.full,
    hmrc                %% "play-frontend-hmrc"         % s"6.7.0-$playVersion"

  )

  val test = Seq(
    hmrc                     %% "service-integration-test"    % "1.1.0-play-28"                    % "test",
    hmrc                     %% "stub-data-generator"         % "1.1.0"                            % "test",
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test-play-28"     % "0.68.0"                           % "test",
    "org.scalatestplus"      %% "scalatestplus-scalacheck"    % "3.1.0.0-RC2"                      % "test",
    "org.scalatestplus"      %% "scalatestplus-mockito"       % "1.0.0-M2"                         % "test",
    "org.scalamock"          %% "scalamock-scalatest-support" % "3.6.0"                            % "test",
    "com.miguno.akka"        %% "akka-mock-scheduler"         % "0.5.5"                            % "test",
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"      % bootstrapBackendVersion            % "test",
    "com.vladsch.flexmark"    % "flexmark-all"                % "0.35.10"                          % "test"
  )

  def apply(): Seq[ModuleID] = compile ++ test

}
