
ThisBuild / version := "1.0.0"
ThisBuild / organization := "dev.tildejustin"
ThisBuild / scalaVersion := "3.5.0"

lazy val root = (project in file("."))
  .settings(
    name := "fabric-updater",
    assembly / mainClass := Some("FabricUpdater"),
    assembly / assemblyJarName := s"${name.value}-${version.value}.jar"
  )

libraryDependencies += "org.scala-lang.modules" %% "scala-swing" % "3.0.0"
libraryDependencies += "com.lihaoyi" %% "os-lib" % "0.10.1"
libraryDependencies += "com.formdev" % "flatlaf" % "3.5"
libraryDependencies += "com.google.code.gson" % "gson" % "2.10.1"
