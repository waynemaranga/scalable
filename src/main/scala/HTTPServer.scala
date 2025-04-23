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
import scala.util.{Try, Success, Failure}
import scala.io.StdIn
import scala.concurrent.{ ExecutionContextExecutor, Future } // Concurrency in Scala: https://docs.scala-lang.org/scala3/book/concurrency.html#introduction
import spray.json._
import ai.AzureOpenAIClient
import ai.AzureJsonProtocol._
import ai.CohereClient
import ai.CompletionResponse
import ai.CohereJsonProtocol._
import db.JDBCClient


object HttpServer {

  /** `HttpServer.start` the HTTP here
    */

  implicit val system: ActorSystem = ActorSystem("simple-system") // container for all actors
  implicit val ec: ExecutionContextExecutor = system.dispatcher
  // ... ↪️ marked implicit because required by many akka methods without explicitly passing them

  // Initialize Cohere client
  val cohereClient = CohereClient(CohereClient.getApiKey())

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
          entity(as[String]) { (message: String) => complete(s"✅ Received: $message") }
        }
      },

      // --- Azure OpenAI endpoint
      path("azure") { 
        post {
          entity(as[String]) { prompt => 
            onComplete(Future(AzureOpenAIClient.formattedComplete(prompt))) {
              case scala.util.Success(scala.util.Success(response)) =>
                complete(HttpEntity(ContentTypes.`application/json`, response.toJson.prettyPrint))
              case scala.util.Success(scala.util.Failure(exception)) =>
                complete(InternalServerError, s"Azure Error: ${exception.getMessage}")
              case scala.util.Failure(exception) =>
                complete(InternalServerError, s"Server Error: ${exception.getMessage}")
            }
          }
        }
      },

      // --- Cohere endpoint
      path("cohere") {
        post {
          entity(as[String]) { prompt =>
            val futureResult = Future {
              cohereClient.complete(prompt)
            }
      
            onComplete(futureResult) {
              case scala.util.Success(scala.util.Success(response)) =>
                complete(HttpEntity(ContentTypes.`application/json`, response.toJson.prettyPrint))
      
              case scala.util.Success(scala.util.Failure(exception)) =>
                complete(InternalServerError, s"Cohere Error: ${exception.getMessage}")
      
              case scala.util.Failure(exception) =>
                complete(InternalServerError, s"Server Error: ${exception.getMessage}")
            }
          }
        }
      },

      pathPrefix("db") {
        concat(
          path("timestamp") {
            get {
              onComplete(Future.fromTry(JDBCClient.getCurrentTimestamp())) { 
                case Success(timestamp) => complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Current Timestamp: $timestamp"))
                case Failure(ex) => complete(HttpResponse(StatusCodes.InternalServerError, entity = s"Error: ${ex.getMessage}"))
              }
            }
          },
          /** */
          path("tables") {
            get {
              onComplete(Future.fromTry(JDBCClient.listTables())) { 
                case Success(tables: List[String]) => 
                  val response = tables.mkString("\n")
                  complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, response))
                case Failure(ex) =>
                  complete(HttpResponse(StatusCodes.InternalServerError, entity = s"Error: ${ex.getMessage}"))
              }
            }
          },
          /** */
          path("columns" / Segment) { tableName =>
            get {
              // Convert Try to Future before passing to onComplete
              onComplete(Future.fromTry(JDBCClient.listColumns(tableName): Try[List[String]])) {
                case Success(columns) =>
                  val response = columns.mkString("\n")
                  complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, response))
                case Failure(ex) =>
                  complete(HttpResponse(StatusCodes.InternalServerError, entity = s"Error: ${ex.getMessage}"))
              }
            }
          },
          /** */
          path("count" / Segment) { tableName =>
            get {
              onComplete(Future.fromTry(JDBCClient.countRows(tableName))) { // <- Change this line
                case Success(count) =>
                  complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"Row count for $tableName: $count"))
                case Failure(ex) =>
                  complete(HttpResponse(StatusCodes.InternalServerError, entity = s"Error: ${ex.getMessage}"))
              }
            }
          }
        )
      }     
    )

  // -- Starting the server with the HTTP instance...
  def start(): Unit = {
    // Start the HTTP server
    val bindingFuture: Future[Http.ServerBinding] =
      Http().newServerAt("localhost", 3254).bind(routes)

    println("🚀 Server online at http://localhost:3254/")
    println("📌 Available endpoints:")
    println("   - GET  /hello")
    println("   - POST /message")
    println("   - POST /azure")
    println("   - POST /cohere")
    println("   - GET  /db/timestamp")
    println("   - GET  /db/tables")
    println("   - GET  /db/columns/<table_name>")
    println("   - GET  /db/count/<table_name>")
    println("⌨️ Press RETURN to stop...")
    StdIn.readLine()

    // Unbind the server and terminate the ActorSystem
    bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())
  }
}