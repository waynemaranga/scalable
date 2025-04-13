/** Cohere API Client */
// Tool Use with Cohere: https://docs.cohere.com/v2/docs/tools
package ai // module namespace

import scala.concurrent.{
  ExecutionContext,
  Future
} // Concurrency in Scala: https://docs.scala-lang.org/scala3/book/concurrency.html#introduction
import scala.util.Try
import org.slf4j.LoggerFactory // Logging: https://www.slf4j.org/manual.html
import akka.actor.ActorSystem // Akka Actors: https://doc.akka.io/libraries/akka-core/current/typed/actors.html#akka-actors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._ // JSON Serialization/Deserialization
import DefaultJsonProtocol._
import scala.concurrent.duration._

// -- Domain models: https://docs.scala-lang.org/scala3/book/taste-modeling.html | Product types: https://docs.scala-lang.org/scala3/book/taste-modeling.html#product-types
// A product type is an algebraic data type (ADT) that only has one shape, for example a singleton object, represented in Scala by a case object; or an immutable structure with accessible fields, represented by a case class.
case class CohereCompletion(
    text: String,
    tokens: Int
) // Cohere completion response; TODO: add more fields
case class CohereToolCallResult(result: Map[String, String])

// JSON formats with more specific types
object JsonFormats extends DefaultJsonProtocol with SprayJsonSupport {

  /** */
  implicit val completionFormat: RootJsonFormat[CohereCompletion] = jsonFormat2(
    CohereCompletion.apply
  )

  /** */
  implicit val toolCallResultFormat: RootJsonFormat[CohereToolCallResult] =
    jsonFormat1(CohereToolCallResult.apply)

  /** */

  // Format for heterogeneous maps - needed for request bodies
  implicit object AnyJsonFormat extends JsonFormat[Any] {
    def write(x: Any): JsValue = x match {
      case n: Int     => JsNumber(n)
      case s: String  => JsString(s)
      case b: Boolean => JsBoolean(b)
      case l: List[_] => JsArray(l.map(write).toVector)
      case m: Map[String, _] =>
        JsObject(m.map { case (k, v) =>
          k -> write(v)
        }) // FIXME: bloop-92 warning
      case _ => JsNull
    }
    def read(value: JsValue): Any = value match {
      case JsNumber(n)       => n.intValue()
      case JsString(s)       => s
      case JsBoolean(b)      => b
      case JsArray(elements) => elements.map(read).toList
      case JsObject(fields)  => fields.map { case (k, v) => k -> read(v) }
      case JsNull            => null
      case JsTrue            => ???
      case JsFalse           => ???
    }
  }
}

class CohereClient(apiKey: String)(implicit
    system: ActorSystem,
    ec: ExecutionContext
) {
  import JsonFormats._

  private val logger = LoggerFactory.getLogger(getClass)
  private val baseUrl = "https://api.cohere.ai/v1"

  // Send a single prompt and get completion
  def complete(
      prompt: String,
      model: String = "command"
  ): Try[CohereCompletion] = {
    logger.info("Sending prompt to Cohere")

    val requestMap = Map[String, Any](
      "model" -> model,
      "prompt" -> prompt,
      "max_tokens" -> 1024
    )

    val requestJson = requestMap.toJson.compactPrint

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$baseUrl/generate",
      headers = List(headers.Authorization(headers.OAuth2BearerToken(apiKey))),
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

        val generationJson = responseJson.asJsObject
          .fields("generations")
          .asInstanceOf[JsArray]
          .elements
          .head
        CohereCompletion(
          text = generationJson.asJsObject.fields("text").convertTo[String],
          tokens = generationJson.asJsObject.fields("tokens").convertTo[Int]
        )
      } else {
        throw new RuntimeException(
          s"Request failed with status: ${response.status}"
        )
      }
    }
  }

  // Send prompt asynchronously
  def completeAsync(
      prompt: String,
      model: String = "command"
  ): Future[CohereCompletion] = {
    logger.info("Sending async prompt to Cohere")

    val requestMap = Map[String, Any](
      "model" -> model,
      "prompt" -> prompt,
      "max_tokens" -> 1024
    )

    val requestJson = requestMap.toJson.compactPrint

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$baseUrl/generate",
      headers = List(headers.Authorization(headers.OAuth2BearerToken(apiKey))),
      entity = HttpEntity(ContentTypes.`application/json`, requestJson),
      protocol = HttpProtocols.`HTTP/1.1`
    )

    for {
      response <- Http().singleRequest(request)
      _ = if (!response.status.isSuccess())
        throw new RuntimeException(
          s"Request failed with status: ${response.status}"
        )
      responseBody <- Unmarshal(response.entity).to[String]
      responseJson = responseBody.parseJson
      generationJson = responseJson.asJsObject
        .fields("generations")
        .asInstanceOf[JsArray]
        .elements
        .head
    } yield CohereCompletion(
      text = generationJson.asJsObject.fields("text").convertTo[String],
      tokens = generationJson.asJsObject.fields("tokens").convertTo[Int]
    )
  }

  // Tool calling implementation
  def toolCall(
      prompt: String,
      tools: List[Map[String, Any]],
      model: String = "command"
  ): Try[CohereToolCallResult] = {
    logger.info("Sending tool call to Cohere")

    val requestMap = Map[String, Any](
      "model" -> model,
      "message" -> prompt,
      "tools" -> tools,
      "tool_use" -> "required"
    )

    val requestJson = requestMap.toJson.compactPrint

    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$baseUrl/chat",
      headers = List(headers.Authorization(headers.OAuth2BearerToken(apiKey))),
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

        val toolCallJson = responseJson.asJsObject
          .fields("tool_calls")
          .asInstanceOf[JsArray]
          .elements
          .head
        val name = toolCallJson.asJsObject.fields("name").convertTo[String]

        CohereToolCallResult(Map("result" -> s"Executed $name"))
      } else {
        throw new RuntimeException(
          s"Request failed with status: ${response.status}"
        )
      }
    }
  }
}

object CohereClient {
  def apply(
      apiKey: String
  )(implicit system: ActorSystem, ec: ExecutionContext): CohereClient =
    new CohereClient(apiKey)
}
