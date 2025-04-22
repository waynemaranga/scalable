package ai

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, ExecutionContext, Await}
import scala.concurrent.duration._
import scala.io.Source
import java.net.{HttpURLConnection, URL}
import java.io.{BufferedReader, InputStreamReader, OutputStream}
import sttp.client3._
import spray.json._
import DefaultJsonProtocol._

case class CompletionResponse(tokens: Int, text: String)
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
      case _ => JsNull
    }
    def read(value: JsValue): Any = value match {
      case JsString(s) => s
      case JsNumber(n) => n.toInt
      case JsBoolean(b) => b
      case _ => null
    }
  }

  implicit def mapFormat[V](implicit valueFormat: JsonFormat[V]): RootJsonFormat[Map[String, V]] = mapFormat[String, V]
  implicit val completionResponseFormat: RootJsonFormat[CompletionResponse] = jsonFormat2(CompletionResponse.apply)
  implicit val toolCallResultFormat: RootJsonFormat[ToolCallResult] = jsonFormat1(ToolCallResult.apply)
}

class CohereClient(apiKey: String)(implicit ec: ExecutionContext) {
  import CohereJsonProtocol._
  
  // HTTP backend for sttp
  val backend = HttpClientSyncBackend()

  def complete(prompt: String): Try[CompletionResponse] = {
    Try {
      val uri = "https://api.cohere.ai/v2/chat"
      val model = "command-a-03-2025"
      
      // Create the request payload
      val payloadMap = Map[String, Any](
        "model" -> model,
        "prompt" -> prompt,
        "max_tokens" -> 1024,
        "temperature" -> 0.7
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
      
      // Parse the JSON response
      val json = responseBody.parseJson.asJsObject
      val text = json.fields("generations").asInstanceOf[JsArray].elements.head.asJsObject.fields("text").convertTo[String]
      val tokens = json.fields("meta").asJsObject.fields("billed_units").asJsObject.fields("output_tokens").convertTo[Int]
      
      CompletionResponse(tokens, text)
    }
  }

  // Add this method to support the model parameter while maintaining the original API
  def completeWithModel(prompt: String, model: String): Try[CompletionResponse] = {
    Try {
      val uri = "https://api.cohere.ai/v2/chat"
      
      // Create the request payload
      val payloadMap = Map[String, Any](
        "model" -> model,
        "prompt" -> prompt,
        "max_tokens" -> 1024,
        "temperature" -> 0.7
      )
      
      val payload = payloadMap.toJson.compactPrint
      
      // Rest of the implementation as before
      val request = basicRequest
        .post(uri"$uri")
        .header("Authorization", s"Bearer $apiKey")
        .header("Content-Type", "application/json")
        .body(payload)
      
      val response = request.send(backend)
      
      val responseBody = response.body match {
        case Right(body) => body
        case Left(errorMsg) => throw new RuntimeException(s"Error: $errorMsg")
      }
      
      val json = responseBody.parseJson.asJsObject
      val text = json.fields("generations").asInstanceOf[JsArray].elements.head.asJsObject.fields("text").convertTo[String]
      val tokens = json.fields("meta").asJsObject.fields("billed_units").asJsObject.fields("output_tokens").convertTo[Int]
      
      CompletionResponse(tokens, text)
    }
  }

  def completeAsync(prompt: String, model: String): Future[CompletionResponse] = {
    Future {
      completeWithModel(prompt, model) match {
        case Success(response) => response
        case Failure(exception) => throw exception
      }
    }
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
        println(s"Response received (${result.tokens} tokens):")
        println(result.text)
      case Failure(exception) =>
        println(s"Error: ${exception.getMessage}")
    }
  }
  
  def testCompletionWithModel(prompt: String, model: String = "command-a-03-2025"): Unit = {
    println(s"Sending prompt with model $model: '$prompt'")
    val client = CohereClient(getApiKey())
    
    client.completeWithModel(prompt, model) match {
      case Success(result) =>
        println(s"Response received (${result.tokens} tokens):")
        println(result.text)
      case Failure(exception) =>
        println(s"Error: ${exception.getMessage}")
    }
  }
  
  def testAsyncCompletion(prompt: String): Unit = {
    println(s"Sending async prompt: '$prompt'")
    val client = CohereClient(getApiKey())
    
    try {
      val result = Await.result(client.completeAsync(prompt, "command-a-03-2025"), 30.seconds)
      println(s"Response received (${result.tokens} tokens):")
      println(result.text)
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
        |Test completion with specific model:
        |  testCompletionWithModel("Your prompt here", "model-name")
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