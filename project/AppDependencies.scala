import sbt._

object AppDependencies {

  val hmrc = "uk.gov.hmrc"
  val playVersion = "play-28"
  val bootstrapBackendVersion = "5.25.0"
  val mockitoScalaVersion = "1.17.12"
  val mongoVersion = "1.3.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"         % mongoVersion,
    hmrc                %% "bootstrap-frontend-play-28" % bootstrapBackendVersion,
    "com.github.kxbmap" %% "configs"                    % "0.6.1",
    "org.typelevel"     %% "cats-core"                  % "2.6.1",
    hmrc                %% "domain"                     % s"8.3.0-$playVersion",
    hmrc                %% "play-frontend-hmrc"         % s"6.7.0-$playVersion",
  )

  val test: Seq[ModuleID] = Seq(
    hmrc                     %% "stub-data-generator"         % "1.1.0"                            % "test",
    "uk.gov.hmrc"            %% "bootstrap-test-play-28"      % bootstrapBackendVersion            % "test",
    "com.vladsch.flexmark"    % "flexmark-all"                % "0.64.6"                           % "test",
    "org.mockito"            %% "mockito-scala"               % mockitoScalaVersion                % "test",
    "org.scalatestplus"      %% "scalacheck-1-17"             % "3.2.16.0"                         % "test",
  )

  def apply(): Seq[ModuleID] = compile ++ test

}
