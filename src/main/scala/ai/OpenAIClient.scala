package ai

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import org.slf4j.LoggerFactory
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import spray.json._
import DefaultJsonProtocol._
import scala.concurrent.duration._

// Domain models
case class OpenAICompletion(text: String, tokens: Int)
case class OpenAIToolCallResult(result: Map[String, String])

// Configuration classes
sealed trait OpenAIConfig
case class StandardOpenAIConfig(apiKey: String) extends OpenAIConfig
case class AzureOpenAIConfig(
    apiKey: String,
    endpoint: String,
    deploymentName: String,
    apiVersion: String = "2023-12-01-preview"
) extends OpenAIConfig

// JSON formats
object OpenAIJsonFormats extends DefaultJsonProtocol {
  implicit val completionFormat: RootJsonFormat[OpenAICompletion] = jsonFormat2(
    OpenAICompletion.apply
  )
  implicit val toolCallResultFormat: RootJsonFormat[OpenAIToolCallResult] =
    jsonFormat1(OpenAIToolCallResult.apply)

  // Simple JSON writer for request bodies
  def toJsonString(data: Map[String, Any]): String = {
    def anyToJsValue(value: Any): JsValue = value match {
      case s: String  => JsString(s)
      case i: Int     => JsNumber(i)
      case d: Double  => JsNumber(d)
      case b: Boolean => JsBoolean(b)
      case l: List[_] => JsArray(l.map(anyToJsValue).toVector)
      case m: Map[_, _] =>
        JsObject(m.asInstanceOf[Map[String, Any]].map { case (k: String, v) =>
          k -> anyToJsValue(v)
        })
      case null => JsNull
      case _    => JsString(value.toString)
    }

    JsObject(data.map { case (k, v) => k -> anyToJsValue(v) }).compactPrint
  }
}

class OpenAIClient(config: OpenAIConfig)(implicit
    system: ActorSystem,
    ec: ExecutionContext
) {
  import OpenAIJsonFormats._

  private val logger = LoggerFactory.getLogger(getClass)

  // Determine base URL and headers based on config type
  private val (baseUrl, authHeader) = config match {
    case StandardOpenAIConfig(apiKey) =>
      (
        "https://api.openai.com/v1",
        headers.Authorization(headers.OAuth2BearerToken(apiKey))
      )
    case AzureOpenAIConfig(apiKey, endpoint, _, apiVersion) =>
      (endpoint, headers.RawHeader("api-key", apiKey))
  }

  // Get endpoint based on config
  private def getEndpoint(path: String): String = config match {
    case StandardOpenAIConfig(_) =>
      s"$baseUrl/$path"
    case AzureOpenAIConfig(_, _, deploymentName, apiVersion) =>
      s"$baseUrl/openai/deployments/$deploymentName/$path?api-version=$apiVersion"
  }

  // Get model parameter based on config type
  private def getModelParam(model: String): (String, String) = config match {
    case StandardOpenAIConfig(_) => ("model", model)
    case AzureOpenAIConfig(_, _, _, _) =>
      ("", "") // Azure uses deployment instead of model parameter
  }

  // Send a single prompt and get completion
  def complete(
      prompt: String,
      model: String = "o3-mini"
  ): Try[OpenAICompletion] = {
    logger.info("Sending prompt to OpenAI")

    val modelParam = getModelParam(model)
    val requestData = Map[String, Any](
      "messages" -> List(Map("role" -> "user", "content" -> prompt)),
      "max_tokens" -> 1024
    ) ++ (if (modelParam._1.nonEmpty) Map(modelParam._1 -> modelParam._2)
          else Map.empty)

    val requestJson = toJsonString(requestData)
    val endpoint = getEndpoint("chat/completions")

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = endpoint,
      headers =
        List(authHeader, headers.RawHeader("Content-Type", "application/json")),
      entity = HttpEntity(ContentTypes.`application/json`, requestJson),
      protocol = HttpProtocols.`HTTP/1.1`
    )

    Try {
      val responseFuture = Http().singleRequest(request)
      val response = scala.concurrent.Await.result(responseFuture, 30.seconds)

      if (response.status.isSuccess()) {
        val responseBodyFuture = Unmarshal(response.entity).to[String]
        val responseBody =
          scala.concurrent.Await.result(responseBodyFuture, 10.seconds)
        val responseJson = responseBody.parseJson

        val choiceJson = responseJson.asJsObject
          .fields("choices")
          .asInstanceOf[JsArray]
          .elements
          .head
        val content = choiceJson.asJsObject
          .fields("message")
          .asJsObject
          .fields("content")
          .convertTo[String]
        val usage = responseJson.asJsObject.fields("usage").asJsObject
        val totalTokens = usage.fields("total_tokens").convertTo[Int]

        OpenAICompletion(text = content, tokens = totalTokens)
      } else {
        val errorFuture = Unmarshal(response.entity).to[String]
        val errorBody = scala.concurrent.Await.result(errorFuture, 10.seconds)
        throw new RuntimeException(
          s"Request failed with status: ${response.status}, body: $errorBody"
        )
      }
    }
  }

  // Send prompt asynchronously
  def completeAsync(
      prompt: String,
      model: String = "gpt-3.5-turbo"
  ): Future[OpenAICompletion] = {
    logger.info("Sending async prompt to OpenAI")

    val modelParam = getModelParam(model)
    val requestData = Map[String, Any](
      "messages" -> List(Map("role" -> "user", "content" -> prompt)),
      "max_tokens" -> 1024
    ) ++ (if (modelParam._1.nonEmpty) Map(modelParam._1 -> modelParam._2)
          else Map.empty)

    val requestJson = toJsonString(requestData)
    val endpoint = getEndpoint("chat/completions")

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = endpoint,
      headers =
        List(authHeader, headers.RawHeader("Content-Type", "application/json")),
      entity = HttpEntity(ContentTypes.`application/json`, requestJson),
      protocol = HttpProtocols.`HTTP/1.1`
    )

    for {
      response <- Http().singleRequest(request)
      _ = if (!response.status.isSuccess()) {
        val errorFuture = Unmarshal(response.entity).to[String]
        val errorBody = scala.concurrent.Await.result(errorFuture, 10.seconds)
        throw new RuntimeException(
          s"Request failed with status: ${response.status}, body: $errorBody"
        )
      }
      responseBody <- Unmarshal(response.entity).to[String]
      responseJson = responseBody.parseJson
      choiceJson = responseJson.asJsObject
        .fields("choices")
        .asInstanceOf[JsArray]
        .elements
        .head
      content = choiceJson.asJsObject
        .fields("message")
        .asJsObject
        .fields("content")
        .convertTo[String]
      usage = responseJson.asJsObject.fields("usage").asJsObject
      totalTokens = usage.fields("total_tokens").convertTo[Int]
    } yield OpenAICompletion(text = content, tokens = totalTokens)
  }

  // Tool calling implementation
  def toolCall(
      prompt: String,
      tools: List[Map[String, Any]],
      model: String = "gpt-3.5-turbo"
  ): Try[OpenAIToolCallResult] = {
    logger.info("Sending tool call to OpenAI")

    val modelParam = getModelParam(model)
    val requestData = Map[String, Any](
      "messages" -> List(Map("role" -> "user", "content" -> prompt)),
      "tools" -> tools,
      "tool_choice" -> "auto",
      "max_tokens" -> 1024
    ) ++ (if (modelParam._1.nonEmpty) Map(modelParam._1 -> modelParam._2)
          else Map.empty)

    val requestJson = toJsonString(requestData)
    val endpoint = getEndpoint("chat/completions")

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = endpoint,
      headers =
        List(authHeader, headers.RawHeader("Content-Type", "application/json")),
      entity = HttpEntity(ContentTypes.`application/json`, requestJson),
      protocol = HttpProtocols.`HTTP/1.1`
    )

    Try {
      val responseFuture = Http().singleRequest(request)
      val response = scala.concurrent.Await.result(responseFuture, 30.seconds)

      if (response.status.isSuccess()) {
        val responseBodyFuture = Unmarshal(response.entity).to[String]
        val responseBody =
          scala.concurrent.Await.result(responseBodyFuture, 10.seconds)
        val responseJson = responseBody.parseJson

        val choiceJson = responseJson.asJsObject
          .fields("choices")
          .asInstanceOf[JsArray]
          .elements
          .head
        val message = choiceJson.asJsObject.fields("message").asJsObject

        if (message.fields.contains("tool_calls")) {
          val toolCallJson = message
            .fields("tool_calls")
            .asInstanceOf[JsArray]
            .elements
            .head
            .asJsObject
          val name = toolCallJson
            .fields("function")
            .asJsObject
            .fields("name")
            .convertTo[String]

          OpenAIToolCallResult(Map("result" -> s"Executed $name"))
        } else {
          // No tool calls were made, just return the content
          val content = message.fields("content").convertTo[String]
          OpenAIToolCallResult(Map("result" -> content))
        }
      } else {
        val errorFuture = Unmarshal(response.entity).to[String]
        val errorBody = scala.concurrent.Await.result(errorFuture, 10.seconds)
        throw new RuntimeException(
          s"Request failed with status: ${response.status}, body: $errorBody"
        )
      }
    }
  }
}

object OpenAIClient {
  def apply(
      apiKey: String
  )(implicit system: ActorSystem, ec: ExecutionContext): OpenAIClient =
    new OpenAIClient(StandardOpenAIConfig(apiKey))

  def apply(
      config: OpenAIConfig
  )(implicit system: ActorSystem, ec: ExecutionContext): OpenAIClient =
    new OpenAIClient(config)

  def azure(
      apiKey: String,
      endpoint: String,
      deploymentName: String,
      apiVersion: String = "2023-12-01-preview"
  )(implicit system: ActorSystem, ec: ExecutionContext): OpenAIClient =
    new OpenAIClient(
      AzureOpenAIConfig(apiKey, endpoint, deploymentName, apiVersion)
    )
}
