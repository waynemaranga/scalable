package ai

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import spray.json._
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Try, Success, Failure}

// JSON support
object AzureJsonProtocol extends DefaultJsonProtocol {
  case class Message(role: String, content: String)
  case class Request(messages: List[Message], max_tokens: Int = 150)
  case class Choice(message: Message)
  case class Response(choices: List[Choice])

  // implicit val msgFormat: RootJsonFormat[Message] = jsonFormat2(Message)
  implicit val msgFormat: RootJsonFormat[Message] = jsonFormat2(ai.AzureJsonProtocol.Message.apply)
  implicit val reqFormat: RootJsonFormat[Request] = jsonFormat2(ai.AzureJsonProtocol.Request.apply)
  implicit val choiceFormat: RootJsonFormat[Choice] = jsonFormat1(ai.AzureJsonProtocol.Choice.apply)
  implicit val resFormat: RootJsonFormat[Response] = jsonFormat1(ai.AzureJsonProtocol.Response.apply)
}

object AzureOpenAIClient {
  import AzureJsonProtocol._

  implicit val system: ActorSystem = ActorSystem("azure-openai")
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  // Load API key from environment or prompt
  val apiKey = sys.env("AZURE_OPENAI_API_KEY")
  val endpoint = sys.env("AZURE_OPENAI_ENDPOINT")
  val deployment = sys.env("AZURE_OPENAI_DEPLOYMENT")
  val apiVersion = sys.env("AZURE_OPENAI_API_VERSION")

  def complete(prompt: String): Try[String] = {
    Try {
      
      val uri = s"$endpoint/openai/deployments/$deployment/chat/completions?api-version=$apiVersion"

      val payload = Request(List(Message("user", prompt)))
      val entity = HttpEntity(ContentTypes.`application/json`, payload.toJson.prettyPrint)

      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = uri,
        headers = List(headers.RawHeader("api-key", apiKey)),
        entity = entity
      )

      val responseFuture: Future[HttpResponse] = Http().singleRequest(request)
      val resultFuture = responseFuture.flatMap(res => Unmarshal(res.entity).to[String])

      val json = Await.result(resultFuture, 20.seconds)
      val parsed = json.parseJson.convertTo[Response]
      
      if (parsed.choices.nonEmpty) {
      parsed.choices.head.message.content
    } else {
      throw new RuntimeException(s"No choices in response: $json")
    }
    }
  }

  def completeAsync(prompt: String): Future[String] = {

    val uri = s"$endpoint/openai/deployments/$deployment/chat/completions?api-version=$apiVersion"

    val payload = Request(List(Message("user", prompt)))
    val entity = HttpEntity(ContentTypes.`application/json`, payload.toJson.prettyPrint)

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = uri,
      headers = List(headers.RawHeader("api-key", apiKey)),
      entity = entity
    )

    for {
      response <- Http().singleRequest(request)
      body <- Unmarshal(response.entity).to[String]
    } yield {
      val parsed = body.parseJson.convertTo[Response]
      parsed.choices.head.message.content
    }
  }

  def testCompletion(prompt: String): Unit = {
    println(s"Sending prompt: '$prompt'")
    complete(prompt) match {
      case Success(result) =>
        println("Response:")
        println(result)
      case Failure(e) =>
        println(s"Error: ${e.getMessage}")
    }
  }

  def testAsyncCompletion(prompt: String): Unit = {
    println(s"Sending async prompt: '$prompt'")
    try {
      val result = Await.result(completeAsync(prompt), 30.seconds)
      println("Response:")
      println(result)
    } catch {
      case e: Exception => println(s"Error: ${e.getMessage}")
    }
  }

  def help(): Unit = {
    println("""
        |Azure OpenAI Shell Testing Guide:
        |
        |Import the module:
        |  import AzureOpenAI._
        |
        |Test completion:
        |  testCompletion("Your prompt here")
        |
        |Test async completion:
        |  testAsyncCompletion("Your prompt here")
        |
        |Set required env variables:
        |  AZURE_OPENAI_KEY
        |  AZURE_OPENAI_ENDPOINT
        |  AZURE_OPENAI_DEPLOYMENT
        |
        |When finished:
        |  system.terminate()
        |""".stripMargin)
  }

//   // Entry point for running as standalone file
//   def main(args: Array[String]): Unit = {
//     if (args.isEmpty) {
//       help()
//     } else {
//       testCompletion(args.mkString(" "))
//       system.terminate()
//     }
//   }
// }
}