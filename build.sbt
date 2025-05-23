scalaVersion := "3.6.4"

// ============================================================================

// Lines like the above defining `scalaVersion` are called "settings". Settings
// are key/value pairs. In the case of `scalaVersion`, the key is "scalaVersion"
// and the value is "2.13.12"

// It's possible to define many kinds of settings, such as:

name := "scalable"
organization := "ch.epfl.scala"
version := "0.0.1"

// You can define other libraries as dependencies in your build like this:
// Typesafe Configuration for JVM languages: https://github.com/lightbend/config?tab=readme-ov-file#api-example

// Dependencies: https://index.scala-lang.org/search?language=3
libraryDependencies ++= Seq(
    // Akka: https://doc.akka.io/libraries/akka-http/current/introduction.html
  "com.typesafe.akka" %% "akka-actor-typed" % "2.8.5", // provides the typed actor system implementation; more typesafe API; builds using the actor model
  "com.typesafe.akka" %% "akka-stream" % "2.8.5", // implements reactive stream for streaming data pipelines
  "com.typesafe.akka" %% "akka-http" % "10.5.2", // http server & client; built on top of Akka Stream
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.5.2", // json marshalling/unmarshalling support for Akka HTTP
  "ch.qos.logback" % "logback-classic" % "1.4.14", // https://logback.qos.ch/manual/introduction.html,
  "org.json4s" %% "json4s-jackson" % "4.0.6", // https://github.com/json4s/json4s?tab=readme-ov-file#guide
  "org.json4s" %% "json4s-core" % "4.0.6",
  "org.json4s" %% "json4s-native" % "4.0.6",
  "com.softwaremill.sttp.client3" %% "core" % "3.9.4", // https://docs.scala-lang.org/toolkit/http-client-intro.html
  "com.microsoft.sqlserver" % "mssql-jdbc" % "12.10.0.jre11", // https://learn.microsoft.com/en-us/azure/azure-sql/database/free-offer?view=azuresql | https://mvnrepository.com/artifact/com.microsoft.sqlserver/mssql-jdbc
  //... ↪️ https://learn.microsoft.com/en-us/sql/connect/jdbc/download-microsoft-jdbc-driver-for-sql-server?view=sql-server-ver16
)

// Here, `libraryDependencies` is a set of dependencies, and by using `+=`,
// we're adding the scala-parser-combinators dependency to the set of dependencies
// that sbt will go and fetch when it starts up.
// Now, in any Scala file, you can import classes, objects, etc., from
// scala-parser-combinators with a regular import.

// TIP: To find the "dependency" that you need to add to the
// `libraryDependencies` set, which in the above example looks like this:

// "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0"

// You can use Scaladex, an index of all known published Scala libraries. There,
// after you find the library you want, you can just copy/paste the dependency
// information that you need into your build file. For example, on the
// scala/scala-parser-combinators Scaladex page,
// https://index.scala-lang.org/scala/scala-parser-combinators, you can copy/paste
// the sbt dependency from the sbt box on the right-hand side of the screen.

// IMPORTANT NOTE: while build files look _kind of_ like regular Scala, it's
// important to note that syntax in *.sbt files doesn't always behave like
// regular Scala. For example, notice in this build file that it's not required
// to put our settings into an enclosing object or class. Always remember that
// sbt is a bit different, semantically, than vanilla Scala.

// ============================================================================

// Most moderately interesting Scala projects don't make use of the very simple
// build file style (called "bare style") used in this build.sbt file. Most
// intermediate Scala projects make use of so-called "multi-project" builds. A
// multi-project build makes it possible to have different folders which sbt can
// be configured differently for. That is, you may wish to have different
// dependencies or different testing frameworks defined for different parts of
// your codebase. Multi-project builds make this possible.

// Here's a quick glimpse of what a multi-project build looks like for this
// build, with only one "subproject" defined, called `root`:

// lazy val root = (project in file(".")).
//   settings(
//     inThisBuild(List(
//       organization := "ch.epfl.scala",
//       scalaVersion := "2.13.12"
//     )),
//     name := "hello-world"
//   )

// To learn more about multi-project builds, head over to the official sbt
// documentation at http://www.scala-sbt.org/documentation.html
