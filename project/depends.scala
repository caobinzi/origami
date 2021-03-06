import sbt._
import Keys._

object depends {

  lazy val scalazVersion     = settingKey[String]("defines the current scalaz version")
  lazy val akkaStreamVersion = settingKey[String]("defines the current akka stream version")

  lazy val versions =
    Seq(scalazVersion,
        akkaStreamVersion)

  def scalaz(scalazVersion: String) =
    Seq("org.scalaz" %% "scalaz-core" % scalazVersion,
        "org.scalaz" %% "scalaz-concurrent" % scalazVersion,
        "org.scalaz" %% "scalaz-effect" % scalazVersion)

  def stream(scalazVersion: String) =
    if (scalazVersion.startsWith("7.1")) Seq("org.scalaz.stream" %% "scalaz-stream" % "0.7a")
    else                                 Seq("org.scalaz.stream" %% "scalaz-stream" % "0.7")

  def akka(akkaStreamVersion: String) = 
    Seq(  "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamVersion)

  lazy val scalacheck =
    Seq("org.scalacheck" %% "scalacheck" % "1.12.2" % "test")

  lazy val disorder =
    Seq("com.ambiata" %% "disorder" % "0.0.1-20150317050225-9c1f81e" % "test")

  lazy val caliper = Seq("com.google.caliper" % "caliper" % "0.5-rc1",
                         "com.google.guava"   % "guava"   % "14.0.1" force())


  lazy val scalameter = Seq("com.storm-enroute" %% "scalameter" % "0.6")
  
  lazy val resolvers =
    sbt.Keys.resolvers ++=
      Seq(
        Resolver.sonatypeRepo("releases"),
        Resolver.sonatypeRepo("snapshots"),
        Resolver.typesafeIvyRepo("releases"),
        Resolver.url("ambiata-oss", new URL("https://ambiata-oss.s3.amazonaws.com"))(Resolver.ivyStylePatterns),
        "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
        "bintray/non" at "http://dl.bintray.com/non/maven"
      )
}
