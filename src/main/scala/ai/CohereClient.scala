package ai

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import sttp.client3._
import spray.json._
import DefaultJsonProtocol._

// Updated response models to match API structure
case class CompletionResponse(id: String, finishReason: String, message: Message, usage: Usage)
case class Message(role: String, content: List[MessageContent])
case class MessageContent(contentType: String, text: String)
case class Usage(billedUnits: BilledUnits, tokens: Tokens)
case class BilledUnits(inputTokens: Int, outputTokens: Int)
case class Tokens(inputTokens: Int, outputTokens: Int)
case class ToolCallResult(result: Map[String, String])

// JSON Protocol for CohereClient
object CohereJsonProtocol extends DefaultJsonProtocol {
  // Support for Map[String, Any] serialization
  implicit val anyJsonFormat: JsonFormat[Any] = new JsonFormat[Any] {
    def write(value: Any): JsValue = value match {
      case s: String => JsString(s)
      case n: Int => JsNumber(n)
      case d: Double => JsNumber(d)
      case b: Boolean => JsBoolean(b)
      case m: Map[_, _] => mapFormat[Any].write(m.asInstanceOf[Map[String, Any]])
      case l: List[_] => listFormat[Any].write(l.asInstanceOf[List[Any]])
      case _ => JsNull
    }
    def read(value: JsValue): Any = value match {
      case JsString(s) => s
      case JsNumber(n) => n.toInt
      case JsBoolean(b) => b
      case _ => null
    }
  }

  // Custom format for MessageContent with field name transformation
  implicit val messageContentFormat: RootJsonFormat[MessageContent] = jsonFormat(
    (contentType: String, text: String) => MessageContent(contentType, text), "type", "text")
  
  implicit val messageFormat: RootJsonFormat[Message] = jsonFormat2(ai.Message.apply)
  
  implicit val billedUnitsFormat: RootJsonFormat[BilledUnits] = jsonFormat(
    (inputTokens: Int, outputTokens: Int) => BilledUnits(inputTokens, outputTokens), "input_tokens", "output_tokens")
  
  implicit val tokensFormat: RootJsonFormat[Tokens] = jsonFormat(
    (inputTokens: Int, outputTokens: Int) => Tokens(inputTokens, outputTokens),
    "input_tokens", "output_tokens"
  )
  
  implicit val usageFormat: RootJsonFormat[Usage] = jsonFormat(
    (billedUnits: BilledUnits, tokens: Tokens) => Usage(billedUnits, tokens),
    "billed_units", "tokens"
  )
  
  // Main response format with field name transformations
  implicit val completionResponseFormat: RootJsonFormat[CompletionResponse] = jsonFormat(
    (id: String, finishReason: String, message: Message, usage: Usage) => 
      CompletionResponse(id, finishReason, message, usage),
    "id", "finish_reason", "message", "usage"
  )

  implicit def mapFormat[V](implicit valueFormat: JsonFormat[V]): RootJsonFormat[Map[String, V]] = mapFormat[String, V]
  implicit val toolCallResultFormat: RootJsonFormat[ToolCallResult] = jsonFormat1(ToolCallResult.apply)
}

class CohereClient(apiKey: String)(implicit ec: ExecutionContext) {
  import CohereJsonProtocol._
  
  // HTTP backend for sttp
  val backend = HttpClientSyncBackend()

  def complete(prompt: String): Try[CompletionResponse] = {
    Try {
      val uri = "https://api.cohere.com/v2/chat"
      val model = "command-a-03-2025"
      
      // Create the request payload with messages format
      val message = Map("role" -> "user", "content" -> prompt)
      val payloadMap = Map[String, Any](
        "model" -> model,
        "messages" -> List(message)
      )
      
      val payload = payloadMap.toJson.compactPrint
      
      // Make the HTTP request
      val request = basicRequest
        .post(uri"$uri")
        .header("Authorization", s"Bearer $apiKey")
        .header("Content-Type", "application/json")
        .body(payload)
      
      val response = request.send(backend)
      
      // Handle the response
      val responseBody = response.body match {
        case Right(body) => body
        case Left(errorMsg) => throw new RuntimeException(s"Error: $errorMsg")
      }
      
      // Parse the JSON response using our updated formats
      val parsedResponse = responseBody.parseJson.convertTo[CompletionResponse]
      parsedResponse
    }
  }

  def completeAsync(prompt: String): Future[CompletionResponse] = {
    Future {
      complete(prompt) match {
        case Success(response) => response
        case Failure(exception) => throw exception
      }
    }(ec)
  }

  def toolCall(prompt: String, tools: List[Map[String, Any]]): Try[ToolCallResult] = {
    // Implement API tool call here
    Success(ToolCallResult(Map("result" -> "Tool call successful")))
  }
}

// Companion object with helper methods
object CohereClient {
  implicit val ec: ExecutionContext = ExecutionContext.global
  
  def apply(apiKey: String)(implicit ec: ExecutionContext = ExecutionContext.global): CohereClient = 
    new CohereClient(apiKey)
  
  def getApiKey(): String = sys.env.getOrElse("COHERE_API_KEY", {
    throw new RuntimeException("COHERE_API_KEY environment variable not set")
  })
  
  def testCompletion(prompt: String): Unit = {
    println(s"Sending prompt: '$prompt'")
    val client = CohereClient(getApiKey())
    
    client.complete(prompt) match {
      case Success(result) =>
        println(s"Response ID: ${result.id}")
        println(s"Finish reason: ${result.finishReason}")
        println(s"Output tokens: ${result.usage.billedUnits.outputTokens}")
        println("Response text:")
        result.message.content.foreach(content => println(content.text))
      case Failure(exception) =>
        println(s"Error: ${exception.getMessage}")
    }
  }
  
  def testAsyncCompletion(prompt: String): Unit = {
    println(s"Sending async prompt: '$prompt'")
    val client = CohereClient(getApiKey())
    
    try {
      val result = Await.result(client.completeAsync(prompt), 30.seconds)
      println(s"Response ID: ${result.id}")
      println(s"Finish reason: ${result.finishReason}")
      println(s"Output tokens: ${result.usage.billedUnits.outputTokens}")
      println("Response text:")
      result.message.content.foreach(content => println(content.text))
    } catch {
      case exception: Exception => println(s"Error: ${exception.getMessage}")
    }
  }
  
  def testToolCall(prompt: String, tools: List[Map[String, Any]]): Unit = {
    println(s"Sending tool call prompt: '$prompt'")
    val client = CohereClient(getApiKey())
    
    client.toolCall(prompt, tools) match {
      case Success(result) =>
        println("Tool call results:")
        result.result.foreach { case (k, v) => println(s"$k: $v") }
      case Failure(exception) =>
        println(s"Error: ${exception.getMessage}")
    }
  }
  
  def help(): Unit = {
    println("""
        |Cohere Shell Testing Guide:
        |
        |Import the module:
        |  import ai.CohereClient._
        |
        |Test completion:
        |  testCompletion("Your prompt here")
        |
        |Test async completion:
        |  testAsyncCompletion("Your prompt here")
        |
        |Test tool call:
        |  testToolCall("Your prompt here", List(Map("name" -> "tool_name", "description" -> "tool description")))
        |
        |Set required env variables:
        |  COHERE_API_KEY
        |""".stripMargin)
  }
}