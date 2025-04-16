// MDN Docs HTTP Guides: https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides
// Akka HTTP: https://doc.akka.io/libraries/akka-http/current/introduction.html#using-akka-http
// Akka HTTP Quickstart for Scala: https://doc.akka.io/libraries/akka-http/current/quickstart-scala/index.html#akka-http-quickstart-for-scala
// Configuration: https://doc.akka.io/libraries/akka-http/current/configuration.html

import akka.actor.ActorSystem // Akka Actors: https://doc.akka.io/libraries/akka-core/current/typed/actors.html#akka-actors
import akka.http.scaladsl.Http // HTTP Model: https://doc.akka.io/libraries/akka-http/current/common/http-model.html#http-model
import akka.http.scaladsl.server.Directives._ // Directives: https://doc.akka.io/libraries/akka-http/current/routing-dsl/directives/index.html#directives
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.Http.ServerBinding
import scala.util.Failure
import scala.io.StdIn
import scala.concurrent.{ ExecutionContextExecutor, Future } // Concurrency in Scala: https://docs.scala-lang.org/scala3/book/concurrency.html#introduction
import spray.json._
import ai.AzureOpenAIClient
import ai.AzureJsonProtocol._

object HttpServer {

  /** `HttpServer.start` the HTTP here
    */

  implicit val system: ActorSystem = ActorSystem("simple-system") // container for all actors
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  // ... ↪️ marked implicit because required by many akka methods without explicitly passing them

  // --- Route(r)
  val routes: Route =
    concat( // Parentheses used for explicit grouping; fixes E018 Error with ~ and inline comments; more robust
      // GET endpoint
      path("hello") { // i.e http://localhost:3254/hello
        get { complete("😃 Hello from Scalable!") }
      },
      // POST endpoint
      path("message") { // i.e http://localhost:3254/message; request body is of type Text
        post {
          entity(as[String]) { message => complete(s"✅ Received: $message") }
        }
      },
      // POST
      path("chat") { 
        // TODO: FIXME: no specification of JSON input; 
        entity(as[String]) { prompt => 
          onComplete(Future(AzureOpenAIClient.formattedComplete(prompt))) {
            //
            case scala.util.Success(scala.util.Success(response)) =>
              complete(HttpEntity(ContentTypes.`application/json`,response.toJson.prettyPrint))
            //
            case scala.util.Success(scala.util.Failure(exception)) =>
              complete(InternalServerError, s"${exception.getMessage}")
            //
            case scala.util.Failure(exception) =>
              complete(InternalServerError, s"${exception.getMessage}")

          }
        }
      }
    )

  // -- Starting the server with the HTTP instance...
  def start(): Unit = {
    // Start the HTTP server
    val bindingFuture: Future[Http.ServerBinding] =
      Http().newServerAt("localhost", 3254).bind(routes)

    println("🚀 Server online at http://localhost:3254/")
    println("⌨️ Press RETURN to stop...")
    StdIn.readLine()

    // Unbind the server and terminate the ActorSystem
    bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())
  }
}