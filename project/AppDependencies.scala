import sbt._

object AppDependencies {

  val hmrc = "uk.gov.hmrc"
  val playVersion = "play-28"
  val bootstrapBackendVersion = "8.3.0"
  val mockitoScalaVersion = "1.17.12"
  val mongoVersion = "1.3.0"

  val compile: Seq[ModuleID] = Seq(
    s"$hmrc.mongo"      %% s"hmrc-mongo-$playVersion"         % mongoVersion,
    hmrc                %% s"bootstrap-frontend-$playVersion" % bootstrapBackendVersion,
    "com.github.kxbmap" %% "configs"                          % "0.6.1",
    "org.typelevel"     %% "cats-core"                        % "2.9.0",
    hmrc                %% "domain"                           % s"8.3.0-$playVersion",
    hmrc                %% s"play-frontend-hmrc-$playVersion"               % "8.5.0"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    hmrc                   %% "stub-data-generator"          % "1.1.0"                 % scope,
    hmrc                   %% s"bootstrap-test-$playVersion" % bootstrapBackendVersion % scope,
    "com.vladsch.flexmark" % "flexmark-all"                  % "0.64.6"                % scope,
    "org.mockito"          %% "mockito-scala"                % mockitoScalaVersion     % scope,
    "org.scalatestplus"    %% "scalacheck-1-17"              % "3.2.16.0"              % scope
  )
}
