import sbt.*

object AppDependencies {

  val hmrc = "uk.gov.hmrc"
  val playVersion = "play-28"
  val bootstrapBackendVersion = "7.16.0"
  val mockitoScalaVersion = "1.17.12"
  val mongoVersion = "1.3.0"

  val compile: Seq[ModuleID] = Seq(
    s"$hmrc.mongo"      %% s"hmrc-mongo-$playVersion"         % mongoVersion,
    hmrc                %% s"bootstrap-frontend-$playVersion" % bootstrapBackendVersion,
    "com.github.kxbmap" %% "configs"                          % "0.6.1",
    "org.typelevel"     %% "cats-core"                        % "2.9.0",
    hmrc                %% "domain"                           % s"8.3.0-$playVersion",
    hmrc                %% "play-frontend-hmrc"               % s"7.29.0-$playVersion"
  )

  val test: Seq[ModuleID] = Seq(
    hmrc                   %% "stub-data-generator"          % "1.1.0"                 % "test",
    hmrc                   %% s"bootstrap-test-$playVersion" % bootstrapBackendVersion % "test",
    "com.vladsch.flexmark" % "flexmark-all"                  % "0.64.6"                % "test",
    "org.mockito"          %% "mockito-scala"                % mockitoScalaVersion     % "test",
    "org.scalatestplus"    %% "scalacheck-1-17"              % "3.2.16.0"              % "test"
  )
}
