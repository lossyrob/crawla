autoCompilerPlugins := true

addCompilerPlugin("org.scala-lang.plugins" % "continuations" % "2.9.1")

scalacOptions += "-P:continuations:enable"

libraryDependencies += "com.github.scopt" %% "scopt" % "2.1.0"

libraryDependencies += "org.jsoup" % "jsoup" % "1.6.1"

libraryDependencies += "com.ning" % "async-http-client" % "1.6.4" % "compile"

// Spray
libraryDependencies ++= Seq(
                    "cc.spray" % "spray-client" % "1.0-M3-SNAPSHOT-20121008",
                    "cc.spray" %%  "spray-json" % "1.1.1"
)

// Akka
libraryDependencies ++= Seq(
                    "com.typesafe.akka" %   "akka-actor" % "2.0.3",
                    "com.typesafe.akka" %   "akka-slf4j" % "2.0.3",
                    "com.typesafe.akka" %   "akka-testkit" % "2.0.3"
)

resolvers += "typesafe repo"  at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "sonatype-public" at "https://oss.sonatype.org/content/groups/public"

resolvers += "spray repo" at "http://repo.spray.cc"


