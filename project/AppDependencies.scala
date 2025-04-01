import sbt.*

object AppDependencies {

  val hmrc = "uk.gov.hmrc"
  val playVersion = "play-30"
  val bootstrapBackendVersion = "8.6.0"
  val mockitoScalaVersion = "1.17.37"
  val mongoVersion = "1.9.0"

  val compile: Seq[ModuleID] = Seq(
    s"$hmrc.mongo"      %% s"hmrc-mongo-$playVersion"         % mongoVersion,
    hmrc                %% s"bootstrap-frontend-$playVersion" % bootstrapBackendVersion,
    "org.typelevel"     %% "cats-core"                        % "2.13.0",
    hmrc                %% s"domain-$playVersion"                           % s"9.0.0",
    hmrc                %% s"play-frontend-hmrc-$playVersion"               % "8.5.0"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    hmrc                   %% "stub-data-generator"          % "1.4.0"                 % scope,
    hmrc                   %% s"bootstrap-test-$playVersion" % bootstrapBackendVersion % scope,
    "org.mockito"          %% "mockito-scala"                % mockitoScalaVersion     % scope,
    "org.scalatestplus"    %% "scalacheck-1-17"              % "3.2.18.0"              % scope
  )
}
