name := """m8chat-admin"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala, SbtWeb)

scalaVersion := "2.11.5"

doc in Compile <<= target.map(_ / "none")

resolvers += Resolver.url("Edulify Repository", url("http://edulify.github.io/modules/releases/"))(Resolver.ivyStylePatterns)

libraryDependencies ++= Seq(
  jdbc,
  anorm,
  cache,
  ws,
  filters,
  "org.postgresql" % "postgresql" % "9.3-1103-jdbc41",
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9" % "test",
  "org.apache.commons" % "commons-email" % "1.3.3",
  "joda-time" % "joda-time" % "2.7",
  "org.joda" % "joda-convert" % "1.7",
  "commons-codec" % "commons-codec" % "1.10",
  "commons-io" % "commons-io" % "2.4",
  "com.edulify" %% "play-hikaricp" % "2.0.2",
  "org.webjars" % "bootstrap" % "3.3.2",
  "org.webjars" % "datatables" % "1.10.4",
  "org.webjars" % "datatables-plugins" % "ca6ec50",
  "org.webjars" % "flot" % "0.8.3",
  "org.webjars" % "font-awesome" % "4.3.0-1",
  "org.webjars" % "holderjs" % "2.4.0",
  "org.webjars" % "jquery" % "2.1.3",
  "org.webjars" % "metisMenu" % "1.1.3",
  "org.webjars" % "mocha" % "2.0.1",
  "org.webjars" % "morrisjs" % "0.5.1",
  "org.webjars" % "raphaeljs" % "2.1.2-1",
  "org.webjars" % "angularjs" % "1.3.10",
  "org.webjars" % "angular-ui-utils" % "47ff7ef35c",
  "org.webjars" % "angular-ui-bootstrap" % "0.12.1-1",
  "org.webjars" % "modernizr" % "2.8.3"
)

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"