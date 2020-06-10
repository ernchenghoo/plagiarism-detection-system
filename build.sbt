

name := """play-scala-seed"""
organization := "plagiarism-detection-system"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)


scalaVersion := "2.13.1"
enablePlugins(JavaAppPackaging)
libraryDependencies ++= Seq(
  guice,
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test,
  "org.jsoup" % "jsoup" % "1.13.1",
  "net.ruippeixotog" %% "scala-scraper" % "2.2.0",
  "org.zeroturnaround" % "zt-zip" % "1.14",
  "mysql" % "mysql-connector-java" % "8.0.20",
  "org.postgresql" % "postgresql" % "9.4-1201-jdbc41",
  "com.amazonaws" % "aws-java-sdk" % "1.3.11",
  javaJdbc
)




// Adds additional packages into Twirl
//TwirlKeys.templateImports += "plagiarism-detection-system.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "plagiarism-detection-system.binders._"
