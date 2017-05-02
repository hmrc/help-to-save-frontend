credentials += Credentials(Path.userHome / ".sbt" / ".credentials")

val hmrcRepoHost = java.lang.System.getProperty("hmrc.repo.host", "https://nexus-build.tax.service.gov.uk")

resolvers ++= Seq(
  "hmrc-snapshots" at hmrcRepoHost + "/content/repositories/hmrc-snapshots",
  "hmrc-releases" at hmrcRepoHost + "/content/repositories/hmrc-releases",
  "typesafe-releases" at hmrcRepoHost + "/content/repositories/typesafe-releases")

//addSbtPlugin("uk.gov.hmrc" % "sbt-common-build" % "latest.integration")

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

addSbtPlugin("com.dadrox" % "sbt-test-reports" % "0.1")

addSbtPlugin("uk.gov.hmrc" % "hmrc-resolvers" % "0.2.0")

//addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.2")

