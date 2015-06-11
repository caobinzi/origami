import sbt._
import Keys._
import depends.scalazVersion
import com.typesafe.sbt._
import com.ambiata.promulgate.project.ProjectPlugin._

object build extends Build {
  type Settings = Def.Setting[_]

  /** MAIN PROJECT */
  lazy val origami = Project(
    id = "origami",
    base = file("."),
    settings = standardSettings ++ promulgate.library("com.ambiata.origami", "ambiata-oss"),
    aggregate = Seq(core, stream)).
    dependsOn(core, stream)

  lazy val standardSettings = Defaults.coreDefaultSettings ++
                              projectSettings              ++
                              compilationSettings          ++
                              testingSettings              ++
                              depends.resolvers

    /** MODULES (sorted in alphabetical order) */
  lazy val core = Project(
    id = "core",
    base = file("core"),
    settings = standardSettings ++ lib("core") ++ prompt ++
      Seq(libraryDependencies ++= depends.scalaz(scalazVersion.value) ++ depends.scalacheck ++ depends.disorder,
          name := "origami-core")
  )

  lazy val stream = Project(
    id = "stream",
    base = file("stream"),
    settings = standardSettings ++ lib("stream") ++
      Seq(libraryDependencies ++= depends.stream(scalazVersion.value) ++ depends.scalacheck ++ depends.caliper,
          name := "origami-stream")
  ).dependsOn(core, core % "test->test")

  lazy val projectSettings: Seq[Settings] = Seq(
    organization := "com.ambiata",
    version in ThisBuild := "1.0",
    scalaVersion := "2.11.6",
    scalazVersion := "7.1.1",
    crossScalaVersions := Seq(scalaVersion.value, "2.10.5"),
    publishArtifact in (Test, packageBin) := true)

  lazy val compilationSettings: Seq[Settings] = Seq(
    javacOptions ++= Seq("-Xmx3G", "-Xms512m", "-Xss4m"),
    maxErrors := 20,
    incOptions := incOptions.value.withNameHashing(true),
    scalacOptions in GlobalScope ++=
      (if (scalaVersion.value == "2.11") Seq("-Xfatal-warnings", "-Xlint", "-Ywarn-unused-import", "-Xcheckinit", "-Xlint", "-deprecation", "-unchecked", "-feature", "-language:_")
       else                              Seq("-Xcheckinit", "-Xlint", "-deprecation", "-unchecked", "-feature", "-language:_")),
    scalacOptions in (Compile, console) ++= Seq("-feature", "-language:_"),
    scalacOptions in (Test, console) ++= Seq("-feature", "-language:_"),
    addCompilerPlugin("org.spire-math" % "kind-projector" % "0.5.2"  cross CrossVersion.binary)
  )

  lazy val testingSettings: Seq[Settings] = Seq(
    logBuffered := false,
    cancelable := true,
    javaOptions ++= Seq("-Xmx3G", "-Xss4M"),
    fork in test := true
  )

  def lib(name: String) =
    promulgate.library(s"com.ambiata.origami.$name", "ambiata-oss")

  lazy val prompt = shellPrompt in ThisBuild := { state =>
    val name = Project.extract(state).currentRef.project
    (if (name == "origami") "" else name) + "> "
  }
}
