// MDN Docs HTTP Guides: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides
// Akka HTTP: https://doc.akka.io/libraries/akka-http/current/introduction.html#using-akka-http
// Akka HTTP Quickstart for Scala: https://doc.akka.io/libraries/akka-http/current/quickstart-scala/index.html#akka-http-quickstart-for-scala
// Configuration: https://doc.akka.io/libraries/akka-http/current/configuration.html

import akka.actor.ActorSystem // Akka Actors: https://doc.akka.io/libraries/akka-core/current/typed/actors.html#akka-actors
import akka.http.scaladsl.Http // HTTP Model: https://doc.akka.io/libraries/akka-http/current/common/http-model.html#http-model
import akka.http.scaladsl.server.Directives._ // Directives: https://doc.akka.io/libraries/akka-http/current/routing-dsl/directives/index.html#directives
import scala.io.StdIn
import scala.concurrent.{ ExecutionContextExecutor, Future } // Concurrency in Scala: https://docs.scala-lang.org/scala3/book/concurrency.html#introduction
import akka.http.scaladsl.Http.ServerBinding

object HttpServer {
  /** `HttpServer.start` the HTTP here
   * 
   * 
   */
  
  def start(): Unit = { // initialises HTTP Server;
    implicit val system: ActorSystem = ActorSystem("simple-system") // container for all actors
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    // ... ↪️ marked implicit because required by many akka methods without explicitly passing them

    // --- Route(r)
    val route =
      ( // Parentheses used for explicit grouping; fixes E018 Error with ~ and inline comments; more robust
        // GET endpoint
        path("hello") { // i.e http://localhost:3254/hello
          get { complete("😃 Hello from Scalable!") }
        } ~ // i.e ~ Returns a Route that chains two Routes. If the first Route rejects the request the second route is given a chance to act upon the request.

          // POST endpoint
          path("message") { // i.e http://localhost:3254/message; request body is of type Text
            post {
              entity(as[String]) { message => complete(s"✅ Received: $message") }
            }
          }
      )
    // -- Starting the server with the HTTP instance...
    // TODO: what is an extension in Scala?
    val bindingFuture: Future[ServerBinding] = Http().newServerAt("localhost", 3254).bind(route) // specify host & port to bind to; attach defined route to server
    // ... ↪️ Cask is probably (definitely) simpler/better for REST APIs

    // --- Lifecycle
    println("🚀 Server online at http://localhost:3254/")
    println("⌨️ Press RETURN to stop...")
    StdIn.readLine()

    // Properly handle the Future[ServerBinding]
    bindingFuture.flatMap(binding => binding.unbind()).onComplete(_ => system.terminate())
  }
}
