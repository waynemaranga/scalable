package ai

import sttp.client3._ 
import spray.json._
import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration._
import scala.util.{Try, Success, Failure}

// JSON support
object AzureJsonProtocol extends DefaultJsonProtocol {
  case class Message(role: String, content: String)
  case class Request(messages: List[Message], max_completion_tokens: Int)
  
  object Request {
    def apply(messages: List[Message], max_completion_tokens: Int = 1024): Request = 
      new Request(messages, max_completion_tokens)
    }
    
  case class Response(choices: List[Choice])
  case class Choice(message: Message)
  case class CustomResponse(content: String, model: String, totalTokens: Int) // TODO: add more response fields

  implicit val msgFormat: RootJsonFormat[Message] = jsonFormat2(ai.AzureJsonProtocol.Message.apply)
  implicit val reqFormat: RootJsonFormat[Request] = jsonFormat2(ai.AzureJsonProtocol.Request.apply)
  // implicit val choiceFormat: RootJsonFormat[Choice] = jsonFormat1(Choice)
  // implicit val responseFormat: RootJsonFormat[Response] = jsonFormat1(Response)

}


// API Client:
object AzureOpenAIClient {
  import AzureJsonProtocol._
  
  implicit val ec: ExecutionContext = ExecutionContext.global
  
  val backend = HttpClientSyncBackend()
  val asyncBackend = HttpClientFutureBackend()

  val apiKey = sys.env("AZURE_OPENAI_API_KEY")
  val endpoint = sys.env("AZURE_OPENAI_ENDPOINT")
  val deployment = sys.env("AZURE_OPENAI_DEPLOYMENT")
  val apiVersion = sys.env("AZURE_OPENAI_API_VERSION")

  /***/
  def parseResponse(jsonStr: String) = {
    val json = jsonStr.parseJson.asJsObject
    val content = json.fields("choices")
                      .asInstanceOf[JsArray].elements.head
                      .asJsObject.fields("message")
                      .asJsObject.fields("content")
                      .convertTo[String]

    val model = json.fields("model").convertTo[String]
    val totalTokens = json.fields("usage").asJsObject.fields("total_tokens").convertTo[Int]

    CustomResponse(content, model, totalTokens)
  }

  /***/
  def complete(prompt: String): Try[String] = {
    Try {
      val uri = s"$endpoint/openai/deployments/$deployment/chat/completions?api-version=$apiVersion"
      val payload = Request(List(Message("user", prompt)))
      val json = payload.toJson.compactPrint
      
      val request = basicRequest
        .post(uri"$uri")
        .header("api-key", apiKey)
        .header("Content-Type", "application/json")
        .body(json)
      
      val response = request.send(backend)
      response.body match {
        case Right(body) => body
        case Left(error) => throw new RuntimeException(s"Error: $error")
      }
    
      
      // val responseBody = response.body match {
      //   case Right(body) => body
      //   case Left(error) => throw new RuntimeException(s"Error: $error")
      // }
      
      // val parsed = responseBody.parseJson.convertTo[Response]
      // if (parsed.choices.nonEmpty) {
      //   parsed.choices.head.message.content
      // } else {
      //   throw new RuntimeException(s"No choices in response: $responseBody")
      // }
    }
  }

  def formattedComplete(prompt: String): Try[CustomResponse] = {
    complete(prompt).map(parseResponse)
  }

  // def completeParsed(prompt: String): Try[CustomResponse] = {
  //   complete(prompt).map { jsonStr =>
  //     val choices = json.fields("choices").asInstanceOf[JsArray].elements
  //     val firstChoice = choices.head.asJsObject
  //     val message = firstChoice.fields("message").asJsObject
  //     val content = message.fields("content").convertTo[String]
  //     val model = json.fields("model").convertTo[String]
  //     val usage = json.fields("usage").asJsObject
  //     val totalTokens = usage.fields("total_tokens").convertTo[Int]
  //     val json = jsonStr.parseJson.asJsObject
      
  //     CustomResponse(content, model, totalTokens)
  //   }
  // }

  // def completeAndParse(prompt: String): Try[OpenAIResponse] = {
  //   // Call the original 'complete' method which returns Try[String]
  //   complete(prompt).map { jsonStr =>
  //     // This block is the inlined logic from the original 'parseResponse'
  //     // It executes only if 'complete(prompt)' was successful.
  //     // Any exception during parsing here will turn the Success into a Failure.
  //     val json = jsonStr.parseJson.asJsObject // Parse the JSON string

  //     // Extract fields - NOTE: This assumes the structure is always correct.
  //     // Consider adding error handling (e.g., using .fields.get(...) or Try)
  //     // if the response structure might vary or be missing fields.
  //     val content = json.fields("choices").asInstanceOf[JsArray].elements.head
  //                       .asJsObject.fields("message").asJsObject.fields("content").convertTo[String]

  //     val model = json.fields("model").convertTo[String] // Extract model

  //     val totalTokens = json.fields("usage").asJsObject.fields("total_tokens").convertTo[Int] // Extract token usage

  //     // Construct the final response object
  //     OpenAIResponse(content, model, totalTokens)
  //   }
  // }
  
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

  // def completeAsync(prompt: String): Future[String] = {
  //   val uri = s"$endpoint/openai/deployments/$deployment/chat/completions?api-version=$apiVersion"
  //   val payload = Request(List(Message("user", prompt)))
  //   val json = payload.toJson.compactPrint
    
  //   val request = basicRequest
  //     .post(uri"$uri")
  //     .header("api-key", apiKey)
  //     .header("Content-Type", "application/json")
  //     .body(json)
    
  //   request.send(asyncBackend).map { response =>
  //     val responseBody = response.body match {
  //       case Right(body) => body
  //       case Left(error) => throw new RuntimeException(s"Error: $error")
  //     }
      
  //     val parsed = responseBody.parseJson.convertTo[Response]
  //     if (parsed.choices.nonEmpty) {
  //       parsed.choices.head.message.content
  //     } else {
  //       throw new RuntimeException(s"No choices in response: $responseBody")
  //     }
  //   }
  // }


  // def testAsyncCompletion(prompt: String): Unit = {
  //   println(s"Sending async prompt: '$prompt'")
  //   try {
  //     val result = Await.result(completeAsync(prompt), 30.seconds)
  //     println("Response:")
  //     println(result)
  //   } catch {
  //     case e: Exception => println(s"Error: ${e.getMessage}")
  //   }
  // }

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
        |  AZURE_OPENAI_API_VERSION
        |""".stripMargin)
  }
}