import sbt._
import sbt.Keys._

object CrawlaBuild extends Build {

  val key = AttributeKey[Boolean]("javaOptionsPatched")

  lazy val root = Project("root", file(".")).settings(
    organization := "com.azavea.crawla",
    name := "crawla",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.10.3",
    
    scalacOptions ++= Seq("-deprecation", 
                          "-unchecked", 
                          "-feature",
                          "-Yclosure-elim",
                          "-language:implicitConversions", 
                          "-language:postfixOps", 
                          "-language:existentials"),
    scalacOptions in Compile in doc ++= Seq("-diagrams", "-implicits"),
    parallelExecution := false,

    fork in test := false,

    mainClass := Some("crawla.Main"),

//    javaOptions in run += "-Xmx2G",

    libraryDependencies ++= Seq(
//      "org.scala-lang" % "scala-reflect" % "2.10.0",
      "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test",
      "com.github.scopt" %% "scopt" % "2.1.0",
      "org.jsoup" % "jsoup" % "1.6.1",
      //  "org.scalaj" %% "scalaj-time" % "0.6",
      "io.spray" % "spray-client" % "1.2-RC2",
      //  "cc.spray" %%  "spray-json" % "1.",
      "com.typesafe.akka" %% "akka-actor" % "2.2.3",
      "com.typesafe.akka" %% "akka-slf4j" % "2.2.3",
      "com.typesafe.akka" %% "akka-testkit" % "2.2.3"

      // "org.scalatest" % "scalatest_2.10" % "2.0.M5b" % "test",
      // "org.scala-lang" % "scala-reflect" % "2.10.2",
      // "junit" % "junit" % "4.5" % "test",
      // "com.vividsolutions" % "jts" % "1.12",
      // "com.typesafe.akka" %% "akka-kernel" % "2.2.1",
      // "com.typesafe.akka" %% "akka-remote" % "2.2.1",
      // "com.typesafe.akka" %% "akka-actor" % "2.2.1",
      // "com.typesafe.akka" %% "akka-cluster" % "2.2.1",
      // "asm" % "asm" % "3.3.1",
      // "org.codehaus.jackson" % "jackson-core-asl" % "1.6.1",
      // "org.codehaus.jackson" % "jackson-mapper-asl" % "1.6.1",
      // "org.spire-math" %% "spire" % "0.4.0",
      // "com.nativelibs4java" %% "scalaxy-loops" % "0.3-SNAPSHOT" % "provided",
      // "net.databinder" %% "dispatch-http" % "0.8.10"
    ),

    resolvers ++= Seq(
      "Scala Test" at "http://www.scala-tools.org/repo-reloases/",
      "spray repo" at "http://repo.spray.cc",
      "Typesafe Repo" at "http://repo.typesafe.com/typesafe/releases/",
      "sonatypeSnapshots" at "http://oss.sonatype.org/content/repositories/snapshots"
    ),

    // Settings to publish to Sonatype
    publishMavenStyle := true,
  
    publishTo <<= version { (v: String) => 
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT")) 
        Some("snapshots" at nexus + "content/repositories/snapshots") 
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },

    publishArtifact in Test := false,

    pomIncludeRepository := { _ => false },
    licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.html")),

    homepage := Some(url("http://github.com/lossyrob/crawla")),

    pomExtra := (

<scm>
  <url>git@github.com:lossyrob/crawla.git</url>
  <connection>scm:git:git@github.com:lossyrob/crawla.git</connection>
</scm>
<developers>
  <developer>
    <id>lossyrob</id>
    <name>Rob Emanuele</name>
    <url>http://github.com/lossyrob/</url>
  </developer>
</developers>
    )
  )

  lazy val server:Project = 
    Project("enterreturn", file("enterreturn"))
      .settings(scalaVersion := "2.10.3")
      .dependsOn(root)
}
