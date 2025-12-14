import scala.collection.Seq

ThisBuild / version           := "0.1.0"
ThisBuild / scalaVersion      := "3.7.4"
ThisBuild / semanticdbEnabled := true

lazy val root = (project in file("."))
  .settings(
    name          := "visual-diff",
    organization  := "com.visualdiff",
    scalacOptions += "-Wunused:all",
    libraryDependencies ++= Seq(
      "org.apache.pdfbox" % "pdfbox" % "3.0.6",
      "org.apache.pdfbox" % "pdfbox-io" % "3.0.6",
      "com.lihaoyi" %% "upickle" % "4.4.1",
      "com.lihaoyi" %% "scalatags" % "0.13.1",
      "com.lihaoyi" %% "mainargs" % "0.7.7",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.6",
      "ch.qos.logback" % "logback-classic" % "1.5.21",
      "org.scalatest" %% "scalatest" % "3.2.19" % Test,
    ),
    assembly / assemblyJarName := "visualdiff.jar",
    assembly / mainClass       := Some("com.visualdiff.cli.Main"),
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
      case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.first
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case "module-info.class" => MergeStrategy.discard
      case _ => MergeStrategy.first
    },
  )
