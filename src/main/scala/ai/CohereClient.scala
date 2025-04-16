// package ai

// import akka.actor.ActorSystem
// import scala.concurrent.ExecutionContext
// import scala.concurrent.Await
// import scala.concurrent.duration._
// import scala.util.{Try, Success, Failure}

// // Main testing object for shell interaction
// object CohereShell {
//   // Set up ActorSystem for testing
//   implicit val system: ActorSystem = ActorSystem("cohere-shell")
//   implicit val ec: ExecutionContext = system.dispatcher

//   // API key loading - reads from environment variable or allows manual entry
//   def getApiKey(): String = {
//     sys.env.getOrElse(
//       "COHERE_API_KEY", {
//         println("COHERE_API_KEY not found in environment.")
//         println("Enter your Cohere API key:")
//         scala.io.StdIn.readLine()
//       }
//     )
//   }

//   // Simple completion test function
//   def testCompletion(prompt: String, model: String = "command"): Unit = {
//     val client = CohereClient(getApiKey())
//     println(s"Sending prompt: '$prompt'")

//     client.complete(prompt, model) match {
//       case Success(completion) =>
//         println(s"Response received (${completion.tokens} tokens):")
//         println(completion.text)
//       case Failure(e) =>
//         println(s"Error: ${e.getMessage}")
//     }
//   }

//   // Test async completion
//   def testAsyncCompletion(prompt: String, model: String = "command"): Unit = {
//     val client = CohereClient(getApiKey())
//     println(s"Sending async prompt: '$prompt'")

//     val future = client.completeAsync(prompt, model)
//     try {
//       val completion = Await.result(future, 30.seconds)
//       println(s"Response received (${completion.tokens} tokens):")
//       println(completion.text)
//     } catch {
//       case e: Exception => println(s"Error: ${e.getMessage}")
//     }
//   }

//   // Test tool calling
//   def testToolCall(
//       prompt: String,
//       toolName: String,
//       params: Map[String, String]
//   ): Unit = {
//     val client = CohereClient(getApiKey())
//     println(s"Testing tool call with: '$prompt'")

//     val tool = Map[String, Any](
//       "name" -> toolName,
//       "description" -> s"$toolName function",
//       "parameter_definitions" -> params.map { case (k, v) =>
//         Map("name" -> k, "description" -> v, "type" -> "string")
//       }.toList
//     )

//     client.toolCall(prompt, List(tool)) match {
//       case Success(result) =>
//         println("Tool call result:")
//         result.result.foreach { case (k, v) => println(s"$k: $v") }
//       case Failure(e) =>
//         println(s"Error: ${e.getMessage}")
//     }
//   }

//   // Cleanup function - should be called when done testing
//   def shutdown(): Unit = {
//     system.terminate()
//   }

//   // Simple help function
//   def help(): Unit = {
//     println("""
//       |Cohere Shell Testing Guide:
//       |
//       |Import the testing module:
//       |  import ai.CohereShell._
//       |
//       |Test completion:
//       |  testCompletion("Your prompt here")
//       |
//       |Test async completion:
//       |  testAsyncCompletion("Your prompt here")
//       |
//       |Test tool call:
//       |  testToolCall("Use calculator to add 5 and 7", "calculator", Map("a" -> "First number", "b" -> "Second number"))
//       |
//       |When finished:
//       |  shutdown()
//       |""".stripMargin)
//   }
// }

// // --- Start sbt
// // $ sbt console

// // --- In the Scala REPL:
// // scala> import ai.CohereShell._
// // scala> help()  // Shows available commands
// // scala> testCompletion("Write a short poem about scala programming")
// // scala> testToolCall("Calculate 15 + 27", "calculator", Map("a" -> "First number", "b" -> "Second number"))
// // scala> shutdown()  // When done testing
